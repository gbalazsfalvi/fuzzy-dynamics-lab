"""Build validation and final protocols without using any held out result."""
import hashlib
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def methods():
    """Return fixed deterministic, learned, random-priority, and eligible scalar comparisons."""
    output = [{"id": name, "kind": "CAE", "strategy": strategy, "model": None, "randomSeed": None}
              for name, strategy in [("uniform", "UNIFORM"), ("maximum-bound", "MAXIMUM_BOUND"),
                                     ("next-step", "NEXT_STEP_BOUND"), ("predicted-gain", "PREDICTED_GAIN_PER_WORK")]]
    for identifier in ["ridge", "mlp-seed-11", "mlp-seed-23", "mlp-seed-37", "mlp-seed-51", "mlp-seed-71"]:
        model = json.loads((ROOT / "learning/models" / f"{identifier}.json").read_text(encoding="utf-8"))
        output.append({"id": identifier, "kind": "CAE", "strategy": "RANKED", "model": model, "randomSeed": None})
    for seed in range(20):
        output.append({"id": f"random-seed-{seed}", "kind": "CAE", "strategy": "RANKED",
                       "model": None, "randomSeed": seed})
    output.extend([{"id": name, "kind": kind, "strategy": None, "model": None, "randomSeed": None}
                   for name, kind in [("alpha-grid", "ALPHA_GRID"), ("published-pwl-core", "PUBLISHED_PWL_CORE")]])
    return output


def protocol(stage):
    """Select base definitions only; no learned output or held out observation is consulted."""
    manifest = json.loads((ROOT / "research/protocols/learning-and-evaluation-split-v1.json").read_text(encoding="utf-8"))
    validation = stage == "VALIDATION_ONLY"
    cohort = [p for p in manifest["problems"]
              if (p["split"] == "validation" if validation else p["split"].startswith("test-"))]
    problems = []
    for problem in cohort:
        index = int(problem["id"].rsplit("-", 1)[1])
        scalar = problem["definition"]["map"] != "COUPLED_TENT"
        problems.append({"id": problem["id"], "baseProblemId": problem["baseProblemId"],
                         "qualifiedReference": not validation and scalar and index % 10 == 0,
                         "definition": problem["definition"],
                         "timingCohort": not validation and index % 10 == 0})
    return {"id": "validation-v1" if validation else "evaluation-locked-v1",
            "date": "2026-11-17", "stage": stage, "precision": 24, "tolerances": ["0.1"],
            "maxRefinements": 512, "maxCells": 513, "maxDepth": 24, "fairnessPeriod": 5,
            "maxPwlPieces": 4096, "warmupRepetitions": 1, "timingRepetitions": 5,
            "orderSeed": 1711202605, "referenceLevels": 64,
            "diagnosticLevels": ["1/200", "1/100", "1/20", "1/10", "1/4", "1/2", "3/4", "1"],
            "methods": methods(), "problems": problems}


def write_once(path, value):
    text = json.dumps(value, indent=2, allow_nan=False) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") != text:
        raise RuntimeError(f"Frozen file differs; create a new version instead of replacing {path.name}.")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def select_on_validation(archive):
    """Prefer certification rate, then median interval work; never select a lucky training seed."""
    expected_protocol = protocol("VALIDATION_ONLY")
    if archive.get("protocol") != expected_protocol:
        raise ValueError("Validation cohort or protocol differs from the predeclared configuration.")
    rows = archive["runs"]
    from decimal import Decimal
    expected_keys = {(p["id"], m["id"], Decimal(t))
                     for p in expected_protocol["problems"]
                     for m in expected_protocol["methods"] for t in expected_protocol["tolerances"]}
    actual_keys = [(row.get("problemId"), row.get("methodId"), Decimal(row.get("tolerance", "-1")))
                   for row in rows]
    if len(actual_keys) != len(expected_keys) or set(actual_keys) != expected_keys:
        raise ValueError("Validation selection requires the complete unique paired cohort.")
    if any(row["status"] not in {"CERTIFIED", "BUDGET_EXHAUSTED", "CANCELLED", "NUMERICAL_FAILURE", "UNAVAILABLE"}
           for row in rows):
        raise ValueError("Validation cohort contains an unsupported outcome.")
    deterministic = ["uniform", "maximum-bound", "next-step", "predicted-gain"]
    def score(identifiers):
        chosen = [r for r in rows if r["methodId"] in identifiers]
        if not chosen:
            raise ValueError("A validation method is missing.")
        return {"certificationRate": sum(r["status"] == "CERTIFIED" for r in chosen) / len(chosen),
                "medianMapImages": statistics.median(r["algorithmIntervalWork"]["mapImages"] for r in chosen)}
    baseline_scores = {name: score([name]) for name in deterministic}
    primary_deterministic = min(deterministic, key=lambda name: (
        -baseline_scores[name]["certificationRate"], baseline_scores[name]["medianMapImages"], name))
    learned = {"ridge": ["ridge"], "mlp": [f"mlp-seed-{seed}" for seed in [11, 23, 37, 51, 71]]}
    learned_scores = {family: score(identifiers) for family, identifiers in learned.items()}
    primary_family = min(learned, key=lambda family: (
        -learned_scores[family]["certificationRate"], learned_scores[family]["medianMapImages"],
        0 if family == "ridge" else 1))
    return {"primaryDeterministicMethod": primary_deterministic,
            "primaryLearnedFamily": primary_family,
            "primaryLearningMethodIds": learned[primary_family],
            "deterministicValidationScores": baseline_scores, "learnedValidationScores": learned_scores}


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("stage", choices=["validation", "final"])
    args = parser.parse_args()
    target = ROOT / "research/protocols" / (
        "validation-v1.json" if args.stage == "validation" else "evaluation-locked-v1.json")
    stage = "VALIDATION_ONLY" if args.stage == "validation" else "FROZEN_HELD_OUT"
    value = protocol(stage)
    if args.stage == "final":
        validation_file = ROOT / "artifacts/validation/manifest.json"
        validation = json.loads(validation_file.read_text(encoding="utf-8"))
        if validation["protocol"]["stage"] != "VALIDATION_ONLY":
            raise ValueError("Selection requires a validation-only artifact.")
        if any(not row["problemId"].startswith("validation-") for row in validation["runs"]):
            raise ValueError("Final test outcomes must not enter method selection.")
        receipt_file = ROOT / "artifacts/verification/validation-replay.json"
        receipt = json.loads(receipt_file.read_text(encoding="utf-8"))
        if (receipt.get("manifestSha256") != digest(validation_file)
                or receipt.get("sourceSha256") != validation["sourceSha256"]
                or receipt.get("protocolSha256") != validation["protocolSha256"]):
            raise ValueError("Validation selection requires a matching successful Java replay receipt.")
        selection = select_on_validation(validation)
        plan_file = ROOT / "docs/ANALYSIS_PLAN.md"
        analysis_file = ROOT / "analysis/evaluate.py"
        if not plan_file.exists() or not analysis_file.exists():
            raise RuntimeError("Freeze the analysis plan and code before generating final results.")
        prefreeze = {"schemaVersion": "evaluation-selection-v1", "date": "2026-11-17",
                     "stage": "FROZEN_BEFORE_HELD_OUT_RESULTS", **selection,
                     "validationManifestSha256": digest(validation_file),
                     "baseSplitManifestSha256": digest(ROOT / "research/protocols/learning-and-evaluation-split-v1.json"),
                     "analysisPlanSha256": digest(plan_file), "analysisCodeSha256": digest(analysis_file),
                     "modelSha256": {name: digest(ROOT / "learning/models" / f"{name}.json")
                                      for name in ["ridge", "mlp-seed-11", "mlp-seed-23",
                                                   "mlp-seed-37", "mlp-seed-51", "mlp-seed-71"]},
                     "timingCohortBaseProblems": [p["baseProblemId"] for p in value["problems"] if p["timingCohort"]],
                     "selectionRule": "Highest validation certification rate, then lowest median map interval work; all MLP seeds retained."}
        write_once(ROOT / "research/protocols/evaluation-selection-v1.json", prefreeze)
    write_once(target, value)
    print(json.dumps({"protocol": str(target), "fileSha256": digest(target),
                      "problems": len(value["problems"]), "methods": len(value["methods"]),
                      "timingCohort": sum(p["timingCohort"] for p in value["problems"])}, indent=2))


if __name__ == "__main__":
    main()
