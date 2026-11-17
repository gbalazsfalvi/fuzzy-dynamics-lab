"""Train isolated empirical gain regressors and export explicit, reviewable dense weights.

No final test result or test feature row is loaded. Python binary floating point is used only for
selection heuristics; Java directed intervals retain sole ownership of every numerical certificate.
"""
import os

# BLAS parallelism is fixed before importing the numerical stack.
for _variable in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS", "NUMEXPR_NUM_THREADS"):
    os.environ[_variable] = "1"

import csv
import hashlib
import importlib.metadata
import json
from pathlib import Path
import platform
import time
import warnings

import numpy as np
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def iteration_count(estimator):
    """Return an estimator's iterative work when it reports such a count."""
    count = getattr(estimator, "n_iter_", None)
    return None if count is None else int(count)


def load_rows(path, features, allowed_groups, split):
    """Reject any forbidden base group, mixed split, missing column, or nonfinite data."""
    values, targets, groups = [], [], []
    with Path(path).open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        required = {"baseProblemId", "path", "split", "signedGain", *features}
        if not required.issubset(set(reader.fieldnames or [])):
            raise ValueError("Training CSV is missing a required feature or grouping column.")
        for row in reader:
            if row["baseProblemId"] not in allowed_groups or row["split"] != split:
                raise ValueError("Forbidden base problem group or mixed split in label CSV.")
            values.append([float(row[name]) for name in features])
            targets.append(float(row["signedGain"]))
            groups.append(row["baseProblemId"])
    x, y = np.asarray(values, dtype=np.float64), np.asarray(targets, dtype=np.float64)
    if not len(values) or not np.isfinite(x).all() or not np.isfinite(y).all():
        raise ValueError("Training features and targets must be finite and nonempty.")
    return x, y, groups


def export_model(identifier, fitted, scaler, target_mean, target_scale, feature_names,
                 data_hash, code_hash, seed):
    """Export weights in input-by-output orientation; the last layer is always linear."""
    if isinstance(fitted, Ridge):
        layers = [{"weights": fitted.coef_.reshape(-1, 1).tolist(),
                   "bias": [float(fitted.intercept_)], "activation": "IDENTITY"}]
    else:
        layers = [{"weights": weights.tolist(), "bias": bias.tolist(),
                   "activation": "IDENTITY" if index == len(fitted.coefs_) - 1 else "TANH"}
                  for index, (weights, bias) in enumerate(zip(fitted.coefs_, fitted.intercepts_))]
    return {"id": identifier, "featureVersion": "cae-cell-features-v1",
            "featureNames": feature_names, "mean": scaler.mean_.tolist(),
            "scale": scaler.scale_.tolist(), "targetMean": target_mean,
            "targetScale": target_scale, "layers": layers, "trainingDataSha256": data_hash,
            "trainingCodeSha256": code_hash, "seed": seed}


def evaluate_export(model, features):
    """Independent exported-format interpreter used against sklearn predictions before Java replay."""
    value = (np.asarray(features) - np.asarray(model["mean"])) / np.asarray(model["scale"])
    for layer in model["layers"]:
        value = value @ np.asarray(layer["weights"]) + np.asarray(layer["bias"])
        if layer["activation"] == "TANH":
            value = np.tanh(value)
    return model["targetMean"] + model["targetScale"] * value[:, 0]


def main():
    root = Path(__file__).resolve().parents[1]
    labels = root / "artifacts/learning/labels"
    manifest_path = root / "research/protocols/learning-and-evaluation-split-v1.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    summary = json.loads((labels / "summary.json").read_text(encoding="utf-8"))
    if manifest["featureVersion"] != summary["featureVersion"]:
        raise ValueError("The feature recipe differs from the frozen training manifest.")
    feature_names = summary["featureNames"]
    allowed_train = {p["baseProblemId"] for p in manifest["problems"] if p["split"] == "train"}
    allowed_validation = {p["baseProblemId"] for p in manifest["problems"] if p["split"] == "validation"}
    if allowed_train & allowed_validation:
        raise ValueError("Training and validation groups overlap.")
    for split in ("train", "validation"):
        if sha256(labels / f"{split}.csv") != summary["dataSha256"][split]:
            raise ValueError("Label CSV digest differs from the Java export evidence.")
    train_x, train_y, train_groups = load_rows(labels / "train.csv", feature_names, allowed_train, "train")
    validation_x, validation_y, validation_groups = load_rows(
        labels / "validation.csv", feature_names, allowed_validation, "validation")

    # Every fitted transform uses training data only, including target standardization.
    scaler = StandardScaler().fit(train_x)
    x = scaler.transform(train_x)
    validation_scaled = scaler.transform(validation_x)
    target_mean = float(train_y.mean())
    target_scale = float(train_y.std()) or 1.0
    target = (train_y - target_mean) / target_scale

    code_digest = hashlib.sha256()
    for path in [Path(__file__), root / "learning/requirements.lock", root / "learning/freeze_manifest.py"]:
        code_digest.update(path.name.encode())
        code_digest.update(path.read_bytes())
    code_hash = code_digest.hexdigest()
    models = root / "learning/models"
    models.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(17112026)
    parity_x = np.vstack([train_x[:32], validation_x[:32],
                          scaler.mean_ + rng.normal(0, 2, (32, len(feature_names))) * scaler.scale_])
    candidates = [("ridge", 0, Ridge(alpha=1.0))]
    for seed in manifest["modelSeeds"]:
        candidates.append((f"mlp-seed-{seed}", seed,
                           MLPRegressor(hidden_layer_sizes=(16,), activation="tanh", solver="lbfgs",
                                        alpha=0.01, max_iter=1200, max_fun=100000,
                                        tol=1e-7, random_state=seed)))
    report_rows, parity_models = [], []
    for identifier, seed, candidate in candidates:
        start = time.perf_counter_ns()
        with warnings.catch_warnings(record=True) as observed_warnings:
            warnings.simplefilter("always")
            candidate.fit(x, target)
        elapsed = time.perf_counter_ns() - start
        predicted = target_mean + target_scale * candidate.predict(validation_scaled)
        model = export_model(identifier, candidate, scaler, target_mean, target_scale, feature_names,
                             summary["dataSha256"]["train"], code_hash, seed)
        expected = target_mean + target_scale * candidate.predict(scaler.transform(parity_x))
        interpreted = evaluate_export(model, parity_x)
        difference = float(np.max(np.abs(expected - interpreted)))
        if not np.allclose(expected, interpreted, atol=1e-10, rtol=1e-10):
            raise RuntimeError("Exported dense weights failed parity against the fitted estimator.")
        path = models / f"{identifier}.json"
        path.write_text(json.dumps(model, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        row = {"id": identifier, "seed": seed, "modelSha256": sha256(path),
               "trainingNanoseconds": elapsed,
               "validationMeanSquaredError": float(mean_squared_error(validation_y, predicted)),
               "validationMeanAbsoluteError": float(mean_absolute_error(validation_y, predicted)),
               "maximumExportParityError": difference,
               "iterations": iteration_count(candidate),
               "warnings": [str(warning.message) for warning in observed_warnings]}
        report_rows.append(row)
        parity_models.append({"id": identifier, "file": path.name, "sha256": sha256(path),
                              "cases": [{"features": features.tolist(), "pythonPrediction": float(value)}
                                        for features, value in zip(parity_x, expected)]})
        print(json.dumps(row), flush=True)
    parity = {"schemaVersion": "dense-ranker-parity-v1", "absoluteTolerance": 1e-10,
              "relativeTolerance": 1e-10, "models": parity_models}
    (models / "parity-cases.json").write_text(json.dumps(parity, indent=2, allow_nan=False) + "\n",
                                             encoding="utf-8")
    report = {"schemaVersion": "learning-training-evidence-v1", "pythonVersion": platform.python_version(),
              "packages": {name: importlib.metadata.version(name)
                           for name in ["numpy", "scipy", "scikit-learn", "joblib", "threadpoolctl"]},
              "manifestSha256": sha256(manifest_path), "trainingDataSha256": summary["dataSha256"]["train"],
              "validationDataSha256": summary["dataSha256"]["validation"],
              "trainingCodeSha256": code_hash, "trainingRows": len(train_y),
              "validationRows": len(validation_y), "trainingGroups": len(set(train_groups)),
              "validationGroups": len(set(validation_groups)), "normalizationFit": "TRAINING_ONLY",
              "target": manifest["primaryTarget"], "fixedArchitecture": "16 tanh hidden units, linear output",
              "ridgeAlpha": 1.0, "mlpAlpha": 0.01,
              "validationZeroPredictorMSE": float(np.mean(validation_y ** 2)),
              "models": report_rows, "finalTestRowsLoaded": 0,
              "scope": "Local empirical gain regressors; validation solves select the evaluation policy later."}
    target_report = root / "artifacts/learning/training-summary.json"
    target_report.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(f"Frozen {len(candidates)} models; no final test outcomes were loaded.", flush=True)


if __name__ == "__main__":
    main()
