"""Analyze saved frozen evaluation evidence without executing a solver or model.

Usage from the repository: learning/.venv/Scripts/python.exe analysis/evaluate.py
The Java saved-evidence replay is a separate prerequisite, not reproduced here.
"""

import argparse
from collections import Counter, defaultdict
import csv
from fractions import Fraction
import gzip
import hashlib
import json
import math
from pathlib import Path
import platform
import re
import statistics
import sys

import numpy as np

from statistics_core import break_even, effects, method_summary, reference_audit

ROOT = Path(__file__).resolve().parents[1]
REGIMES = ("id-1d", "id-2d", "ood-1d", "ood-2d")
CSV_FILES = ("outcomes.csv", "measurements.csv", "alpha-diagnostics.csv", "reference-errors.csv", "refinement-curves.csv")
OUTCOME_FIELDS = "run problem method tolerance status reason maximumBound geometryElements medianTotalNanoseconds algorithmMapImages algorithmLipschitzBounds algorithmMembershipRanges algorithmExactPointEvaluations verificationMapImages verificationExactPointEvaluations configurationSha256".split()
TIMING_FIELDS = "run repetition orderIndex solveNanoseconds verificationNanoseconds totalNanoseconds".split()
MAX_PAYLOAD_BYTES = 64 * 1024 * 1024


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON field: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"), object_pairs_hook=_unique_object,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"Nonfinite JSON: {value}")))


def validate_payload_json(path):
    """Decode one already-hash-checked payload with at most 64 MiB of JSON bytes.

    Both plain and gzip payloads are accepted. Parsing establishes JSON shape,
    not numerical truth; the separate Java evidence replay remains necessary.
    Nothing is retained across payloads, avoiding a whole-cohort geometry cache.
    """
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rb") as stream:
        content = stream.read(MAX_PAYLOAD_BYTES + 1)
    if len(content) > MAX_PAYLOAD_BYTES:
        raise ValueError(f"Decoded evidence payload exceeds {MAX_PAYLOAD_BYTES} byte size limit: {path.name}")
    payload = json.loads(content.decode("utf-8-sig"), object_pairs_hook=_unique_object,
                         parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"Nonfinite payload JSON: {value}")))
    if not isinstance(payload, dict):
        raise ValueError("Numerical evidence payload must be a JSON object")


def read_csv(path):
    with Path(path).open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or len(set(reader.fieldnames)) != len(reader.fieldnames):
            raise ValueError(f"Invalid CSV header: {path}")
        rows = list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        raise ValueError(f"Malformed CSV row: {path}")
    return rows


def _regime(problem_id):
    matches = [regime for regime in REGIMES if problem_id.startswith(f"test-{regime}-")]
    if len(matches) != 1:
        raise ValueError(f"Unknown frozen test regime: {problem_id}")
    return matches[0]


def _exact(value, name):
    if not isinstance(value, str):
        raise ValueError(f"Exact {name} must be a string")
    return Fraction(value)


def _integer(value, name):
    if isinstance(value, bool) or str(value) != str(int(value)) or int(value) < 0:
        raise ValueError(f"Expected nonnegative integer {name}")
    return int(value)


def _assert_equal(actual, expected, message):
    if actual != expected:
        raise ValueError(message)


def load_bundle(directory, freeze_path):
    """Validate saved CSV/manifest agreement, complete cohort, timing flags and hashes."""
    directory, freeze_path = Path(directory).resolve(), Path(freeze_path).resolve()
    archive, freeze = read_json(directory / "manifest.json"), read_json(freeze_path)
    _assert_equal(archive.get("schemaVersion"), "comparison-evidence-v1", "Unsupported evidence schema")
    for key in ["sourceSha256", "protocolSha256"]:
        if not re.fullmatch(r"[0-9a-f]{64}", str(freeze.get(key, ""))):
            raise ValueError(f"Missing frozen {key}")
        _assert_equal(archive.get(key), freeze[key], f"Frozen {key} mismatch")
    for relative, digest in freeze.get("analysisSha256", {}).items():
        if not (relative == "docs/ANALYSIS_PLAN.md" or re.fullmatch(r"analysis/[a-zA-Z0-9_.-]+\.py", relative)):
            raise ValueError("Unsafe frozen analysis hash path")
        _assert_equal(sha256(ROOT / relative), digest, f"Frozen analysis hash mismatch: {relative}")
    protocol = archive["protocol"]
    _assert_equal(protocol.get("stage"), "FROZEN_HELD_OUT", "Analysis requires frozen held-out protocol")
    comparator, learned = freeze["primaryDeterministicMethod"], freeze["primaryLearningMethodIds"]
    if comparator not in {"uniform", "maximum-bound", "next-step", "predicted-gain"}:
        raise ValueError("Primary deterministic comparator cannot be selected from stochastic/test methods")
    expected_learned = (["ridge"] if freeze.get("primaryLearnedFamily") == "ridge" else
                        [f"mlp-seed-{seed}" for seed in [11, 23, 37, 51, 71]]
                        if freeze.get("primaryLearnedFamily") == "mlp" else [])
    if not expected_learned or sorted(learned) != sorted(expected_learned):
        raise ValueError("Frozen learned family must retain all declared model seeds")
    problems, bases, methods = {}, {}, [method["id"] for method in protocol["methods"]]
    if len(methods) != len(set(methods)) or not set([comparator, *learned]) <= set(methods):
        raise ValueError("Invalid declared method cohort")
    for problem in protocol["problems"]:
        identifier = problem["id"]
        if identifier in problems:
            raise ValueError("Duplicate declared problem")
        regime, base = _regime(identifier), problem["baseProblemId"]
        if base in bases and bases[base] != regime:
            raise ValueError("A base problem occurs in more than one regime")
        if type(problem.get("timingCohort")) is not bool:
            raise ValueError("Every problem needs an explicit timingCohort flag")
        bases[base] = regime
        problems[identifier] = dict(problem, regime=regime)
    for base in bases:
        if len({p["timingCohort"] for p in problems.values() if p["baseProblemId"] == base}) != 1:
            raise ValueError("Timing cohort must retain every variant of a base problem")
    tolerances = [_exact(value, "protocol tolerance") for value in protocol["tolerances"]]
    if len(tolerances) != len(set(tolerances)) or not all(0 < value < 1 for value in tolerances):
        raise ValueError("Invalid declared tolerance cohort")
    expected = {(problem, method, tolerance) for problem in problems for method in methods for tolerance in tolerances}
    manifest_runs = archive["runs"]
    by_id = {run["id"]: run for run in manifest_runs}
    actual = {(run["problemId"], run["methodId"], _exact(run["tolerance"], "run tolerance")) for run in manifest_runs}
    if not expected or len(by_id) != len(manifest_runs) or len(actual) != len(manifest_runs) or expected != actual:
        raise ValueError("Manifest does not contain the complete paired outcome cohort")
    files = {name: read_csv(directory / name) for name in CSV_FILES}
    outcomes = files["outcomes.csv"]
    if len(outcomes) != len(by_id) or {row["run"] for row in outcomes} != set(by_id):
        raise ValueError("CSV outcome cohort is incomplete or duplicated")
    measurements = defaultdict(list)
    for row in files["measurements.csv"]:
        if row["run"] not in by_id:
            raise ValueError("Timing refers to unknown run")
        if set(row) != set(TIMING_FIELDS):
            raise ValueError("Unexpected timing CSV schema")
        measurements[row["run"]].append({key: _integer(row[key], key) for key in TIMING_FIELDS if key != "run"})
    hashes = {name: sha256(directory / name) for name in ["manifest.json", *CSV_FILES]}
    normalized = []
    for row in outcomes:
        if set(row) != set(OUTCOME_FIELDS):
            raise ValueError("Unexpected outcome CSV schema")
        run = by_id[row["run"]]
        problem = problems[run["problemId"]]
        unavailable = run["status"] == "UNAVAILABLE"
        comparisons = dict(run=run["id"], problem=run["problemId"], method=run["methodId"],
                           status=run["status"], reason=run["reason"], configurationSha256=run["configurationSha256"])
        for key, value in comparisons.items():
            _assert_equal(row[key], value, f"CSV outcome differs from manifest: {key}, {row['run']}")
        _assert_equal(Fraction(row["tolerance"]), _exact(run["tolerance"], "tolerance"), "CSV tolerance differs from manifest")
        bound = None if run["maximumBound"] is None else _exact(run["maximumBound"], "maximumBound")
        _assert_equal(None if row["maximumBound"] == "" else Fraction(row["maximumBound"]), bound,
                      "CSV outcome bound differs from manifest")
        if bound is not None and not 0 <= bound <= 1:
            raise ValueError("Invalid maximum certificate bound")
        if run["status"] == "CERTIFIED" and (bound is None or bound > Fraction(row["tolerance"])):
            raise ValueError("Certified outcome does not satisfy its tolerance")
        counter_values = dict(geometryElements=run["geometrySize"],
            algorithmMapImages=run["algorithmIntervalWork"]["mapImages"],
            algorithmLipschitzBounds=run["algorithmIntervalWork"]["lipschitzBounds"],
            algorithmMembershipRanges=run["algorithmIntervalWork"]["membershipRanges"],
            algorithmExactPointEvaluations=run["algorithmExactPointEvaluations"],
            verificationMapImages=run["verificationWork"]["intervalWork"]["mapImages"],
            verificationExactPointEvaluations=run["verificationWork"]["exactPointEvaluations"])
        for key, value in counter_values.items():
            _assert_equal(_integer(row[key], key), _integer(value, key), f"CSV outcome counter differs from manifest: {key}")
        timings = sorted(measurements[row["run"]], key=lambda item: item["repetition"])
        stored_timings = sorted(run["timings"], key=lambda item: item["repetition"])
        _assert_equal(timings, stored_timings, "CSV timing observations differ from manifest")
        repetitions = protocol["timingRepetitions"] if problem["timingCohort"] else 1
        if problem["timingCohort"] and (repetitions < 5 or protocol["warmupRepetitions"] < 1):
            raise ValueError("Timing cohort requires warmup and at least five timed repetitions")
        _assert_equal([timing["repetition"] for timing in timings], [] if unavailable else list(range(repetitions)),
                      "Incorrect timing repetition cohort")
        for timing in timings:
            if timing["totalNanoseconds"] < timing["solveNanoseconds"] + timing["verificationNanoseconds"]:
                raise ValueError("Inconsistent total/solve/verification timing")
        median = None if unavailable else sorted(t["totalNanoseconds"] for t in timings)[len(timings)//2]
        _assert_equal(None if row["medianTotalNanoseconds"] == "" else int(row["medianTotalNanoseconds"]),
                      median, "CSV median timing differs from saved observations")
        if unavailable:
            if run["evidenceFile"] is not None or run["evidenceSha256"] is not None:
                raise ValueError("Unavailable method carries evidence")
        else:
            relative = run["evidenceFile"]
            if not isinstance(relative, str) or "\\" in relative or not re.fullmatch(r"runs/[a-zA-Z0-9_.-]+\.json(?:\.gz)?", relative):
                raise ValueError("Unsafe evidence path")
            path = (directory / relative).resolve()
            if not path.is_relative_to(directory):
                raise ValueError("Evidence path escapes input directory")
            digest = sha256(path)
            _assert_equal(digest, run["evidenceSha256"], f"Numerical evidence hash mismatch: {relative}")
            validate_payload_json(path)
            hashes[relative] = digest
        normalized.append(dict(row, **counter_values, base=problem["baseProblemId"], regime=problem["regime"],
                               tolerance=str(Fraction(row["tolerance"])),
                               timingCohort=problem["timingCohort"], maximumBound=bound,
                               totalSeconds=None if median is None else median/1e9,
                               verificationSeconds=None if unavailable else statistics.median(t["verificationNanoseconds"] for t in timings)/1e9,
                               timings=timings))
    # Independent CSV reports may not silently omit bad diagnostics from the manifest.
    for filename, field in [("alpha-diagnostics.csv", "cuts"), ("reference-errors.csv", "referenceErrors")]:
        expected_rows = []
        for run in manifest_runs:
            for diagnostic in run["diagnostics"][field]:
                expected_rows.append({"run": run["id"], **{key: "" if value is None else str(value)
                                                            for key, value in diagnostic.items()}})
        canonical = lambda data: sorted(json.dumps(row, sort_keys=True) for row in data)
        _assert_equal(canonical(files[filename]), canonical(expected_rows), f"Diagnostic CSV differs from manifest: {filename}")
    for row in files["alpha-diagnostics.csv"]:
        if row["status"] in {"BOTH_EMPTY", "REFERENCE_EMPTY", "APPROXIMATION_EMPTY"} and row["hausdorff"] != "":
            raise ValueError("Empty alpha cuts cannot carry an ordinary Hausdorff distance")
    for row in files["refinement-curves.csv"]:
        if row["run"] not in by_id:
            raise ValueError("Refinement history refers to unknown run")
    return dict(rows=normalized, archive=archive, freeze=freeze, outcomes=outcomes,
                measurements=files["measurements.csv"], references=files["reference-errors.csv"],
                alpha=files["alpha-diagnostics.csv"], refinements=files["refinement-curves.csv"],
                inputHashes=hashes, freezeHash=sha256(freeze_path))


def write_csv(path, rows, fields=None):
    fields = fields or (list(rows[0]) if rows else [])
    with Path(path).open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: json.dumps(value, sort_keys=True) if isinstance(value, (dict, list)) else value
                             for key, value in row.items()})


def _text(value):
    return "unavailable" if value is None else f"{value:.6g}"


def report_text(result):
    primary = next(item for item in result["effects"] if item["endpoint"] == "certificationDifference" and item["scope"] == "all")
    text = ["# Frozen evaluation analysis", "", f"Analysis version: {result['analysisVersion']}. Protocol: `{result['protocolId']}`.", "",
            f"Primary comparison: {result['primaryLearnedFamily']} ({len(result['primaryLearningMethodIds'])} retained model(s)) versus {result['primaryDeterministicMethod']}.", "",
            f"Equal-regime certification-rate difference: **{_text(primary['estimate'])}**; 95% grouped percentile interval: {primary['ci95']}. Positive favors the learned family.", "",
            "This is the primary endpoint. Problem variants remain within base clusters, and the same resampled model-seed indices are shared across all bases and regimes. All other intervals are descriptive.", "",
            "## Complete outcomes", "", "Every method seed remains a separate row; unavailable methods remain visible.", "",
            "| Regime | Method | Bases | Requested | Eligible | Certified | Budget exits | Other failures | Unavailable | Timing bases |", "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for row in result["methodSummary"]:
        text.append("| " + " | ".join(str(row[key]) for key in ["regime", "method", "baseProblems", "requested", "eligible", "certified", "budgetExhausted", "otherFailures", "unavailable", "timingBases"]) + " |")
    text += ["", "## Paired effects", "",
             "Runtime ratios use only the preselected timing cohort and are conditional on both methods certifying. Values below one favor the learned family for that subset. They are not unconditional speedups. The noncohort single observations do not enter runtime conclusions.", "",
             "| Endpoint | Scope | Estimate | 95% interval | Bases | Both certified / declared pairs | Valid bootstrap draws |",
             "|---|---|---:|---|---:|---:|---:|"]
    for row in result["effects"]:
        text.append(f"| {row['endpoint']} | {row['scope']} | {_text(row['estimate'])} | {row['ci95']} | {row['baseProblems']} | {row['bothCertifiedPairs']} / {row['declaredPairs']} | {row['validBootstrapReplicates']} / {row['bootstrapReplicates']} |")
    audit = result["referenceAudit"]
    text += ["", "## Correctness and diagnostics", "",
             f"Qualified reference rows: {audit['qualifiedRows']}; verified lower-bound violations: **{len(audit['violations'])}**; upper-only exceedances (inconclusive): {audit['upperOnlyExceedances']}; unqualified rows: {audit['unqualifiedRows']}.", "",
             ("A verified violation blocks a correctness claim. Affected runs: " + ", ".join(audit["violations"]) if audit["violations"] else
              "No violation was found in these saved qualified references. This is scalar reference evidence, not a continuous proof or joint two-dimensional validation."), "",
             "Alpha-cut status counts (empty-cut distances remain absent):", "", "```json", json.dumps(result["alphaStatusCounts"], indent=2, sort_keys=True), "```", "",
             "## Timing and amortization", "",
             "Observed completion curves retain all eligible failures in the denominator. They use run medians, not within-run first-hit times; they are not Kaplan-Meier estimates. Each timing regime has only its preselected bases, with five repeated observations per eligible run, not five new scientific instances.", "",
             "Solve plus post-solve verification is timed, including in-solve learned inference. Reference preparation, diagnostics, report I/O and training are excluded. Cold JVM startup is unmeasured. Verification duration and its per-run fraction appear in run-timing-summary.csv; single noncohort observations are labeled excluded.", "",
             "Training amortization: " + json.dumps(result["trainingBreakEven"], sort_keys=True) + ".", "",
             "Environment recorded by the producer:", "", "```json", json.dumps(result["environment"], indent=2, sort_keys=True), "```", "",
             "## Scope and limitations", "",
             "These are finite-cohort, local timing observations with uncontrolled external load. Resampling intervals do not establish universal performance; degenerate all-success/all-failure intervals do not prove zero population uncertainty. The timing subset is much smaller than the success/work cohort. Model-seed uncertainty is available only for families with multiple retained training seeds. Random-priority seed results are descriptive controls, not a selected winning seed.", "",
             "Endograph distance can hide weak membership components; positive alpha-cut diagnostics remain necessary. Qualified continuous reference geometry is scalar only. Fixed precision, budgets and horizons limit the conclusions. Full-text comparison with the closest 2007 and 2016 papers and external expert mathematical review remain open; no novelty claim follows from this analysis.", "",
             "Hash consistency is provenance, not authentication or an independent mathematical proof. The separate Java evidence replay is required. This script reads saved evidence and does not rerun or tune a solver/model.", "",
             "Design coverage:", "", "```json", json.dumps(result["designCoverage"], indent=2, sort_keys=True), "```", "",
             "Standalone figures are in `figures/` as PNG, SVG and PDF. Raw input artifact hashes, analysis source hashes, bootstrap settings, and library versions are in `analysis.json`.", ""]
    return "\n".join(text)


def analyze(directory, freeze_path, output, replicates=10_000, seed=20261117, training_cost=None):
    directory, output, freeze_path = Path(directory).resolve(), Path(output).resolve(), Path(freeze_path).resolve()
    if output == directory or output.is_relative_to(directory) or directory.is_relative_to(output) or freeze_path.is_relative_to(output):
        raise ValueError("Output must be separate from input evidence and freeze")
    bundle = load_bundle(directory, freeze_path)
    rows, freeze = bundle["rows"], bundle["freeze"]
    cost = read_json(training_cost) if training_cost else None
    import matplotlib
    from figures import render_figures
    summary = method_summary(rows)
    coverage = {regime: dict(baseProblems=len({row["base"] for row in rows if row["regime"] == regime}),
                            timingBases=len({row["base"] for row in rows if row["regime"] == regime and row["timingCohort"]}))
                for regime in REGIMES}
    result = dict(analysisVersion="analysis-v1", protocolId=bundle["archive"]["protocol"]["id"],
                  primaryLearnedFamily=freeze["primaryLearnedFamily"],
                  primaryDeterministicMethod=freeze["primaryDeterministicMethod"],
                  primaryLearningMethodIds=freeze["primaryLearningMethodIds"],
                  effects=effects(rows, freeze["primaryDeterministicMethod"], freeze["primaryLearningMethodIds"], replicates, seed),
                  methodSummary=summary, referenceAudit=reference_audit(bundle["references"]),
                  alphaStatusCounts=dict(Counter(row["status"] for row in bundle["alpha"])),
                  trainingBreakEven=break_even(rows, freeze["primaryDeterministicMethod"], freeze["primaryLearningMethodIds"], cost),
                  environment=bundle["archive"].get("environment", {}), designCoverage=coverage,
                  declaredMethods=[method["id"] for method in bundle["archive"]["protocol"]["methods"]],
                  inputHashes=bundle["inputHashes"], freezeSha256=bundle["freezeHash"],
                  frozenSourceSha256=freeze["sourceSha256"], frozenProtocolSha256=freeze["protocolSha256"],
                  frozenSelection=freeze,
                  bootstrap=dict(replicates=replicates, seed=seed, percentileInterval=[0.025, 0.975],
                                 units="base problems within regime and globally shared model seeds"),
                  runtime=dict(python=platform.python_version(), numpy=np.__version__, matplotlib=matplotlib.__version__),
                  analysisHashes={path.name: sha256(path) for path in sorted(Path(__file__).parent.glob("*.py"))},
                  analysisPlanSha256=sha256(ROOT / "docs" / "ANALYSIS_PLAN.md"))
    if training_cost:
        result["trainingCostSha256"] = sha256(training_cost)
    output.mkdir(parents=True, exist_ok=True)
    write_csv(output / "method-regime-summary.csv", summary)
    write_csv(output / "primary-effects.csv", result["effects"])
    timing_rows = []
    for row in rows:
        total, verification = row["totalSeconds"], row["verificationSeconds"]
        timing_rows.append(dict(run=row["run"], baseProblemId=row["base"], regime=row["regime"], method=row["method"],
                               status=row["status"], includedInTimingAnalysis=row["timingCohort"] and row["status"] != "UNAVAILABLE",
                               observations=len(row["timings"]), medianTotalSeconds=total,
                               medianVerificationSeconds=verification,
                               verificationFraction=verification/total if total and verification is not None else None))
    write_csv(output / "run-timing-summary.csv", timing_rows)
    render_figures(bundle, result, output / "figures")
    (output / "analysis.json").write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False)+"\n", encoding="utf-8")
    (output / "report.md").write_text(report_text(result), encoding="utf-8")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / "artifacts" / "evaluation")
    parser.add_argument("--freeze", type=Path, default=ROOT / "research" / "evaluation-freeze-v1.json")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts" / "evaluation-analysis")
    parser.add_argument("--training-cost", type=Path, help="Explicit complete comparable training cost JSON; otherwise unavailable")
    args = parser.parse_args()
    try:
        result = analyze(args.input, args.freeze, args.output, training_cost=args.training_cost)
    except (ValueError, KeyError, OSError, TypeError) as error:
        print(f"Analysis rejected input: {error}", file=sys.stderr)
        return 1
    print(f"Saved analysis to {args.output.resolve()}; verified reference violations: {len(result['referenceAudit']['violations'])}")
    return 2 if result["referenceAudit"]["violations"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
