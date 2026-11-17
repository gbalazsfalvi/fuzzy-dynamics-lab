"""Create deterministic allowlisted release archives from completed saved evidence.

Standard library only. This command never generates numerical evidence, trains,
selects models, runs analysis, fabricates verification receipts or publishes.
Full and curated bundles retain original bytes and distinguish their replay scope.
"""

from __future__ import annotations

import argparse
from collections import Counter
import csv
from fractions import Fraction
import gzip
import hashlib
import itertools
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import zipfile

try:
    from .analyze_verified import validate as verify_final_replay
except ImportError:
    from analyze_verified import validate as verify_final_replay

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.0"
REGIMES = ("id-1d", "id-2d", "ood-1d", "ood-2d")
CSV_NAMES = ("outcomes.csv", "measurements.csv", "alpha-diagnostics.csv",
             "reference-errors.csv", "refinement-curves.csv")
FIGURE_NAMES = ("outcome-heatmap", "paired-algorithm-work", "observed-completion",
                "qualified-reference-certificate", "bootstrap-effects")
MODEL_NAMES = ("ridge", "mlp-seed-11", "mlp-seed-23", "mlp-seed-37", "mlp-seed-51", "mlp-seed-71")
SHARED_FILES = (
    "LICENSE", "NOTICE", "CITATION.cff", "SECURITY.md", "README.md",
    "docs/REPRODUCIBILITY.md", "learning/README.md", "learning/MODEL_CARD.md",
    "docs/WORKBENCH.md", "docs/API_CONTRACT.md",
    "docs/milestones/00-01.md", "docs/milestones/02.md", "docs/milestones/03.md",
    "docs/milestones/04.md", "docs/milestones/05.md", "docs/milestones/06.md", "docs/milestones/07.md",
    "docs/images/workbench-1d.png", "docs/images/workbench-2d.png", "docs/images/comparison-evidence.png",
    "artifacts/foundation-demo.json", "artifacts/cae-demo.json", "artifacts/cae-demo-summary.md",
    "docs/NUMERICAL_CONTRACT.md", "docs/CAE_METHOD.md", "docs/COMPARISON_METHOD.md",
    "docs/ANALYSIS_PLAN.md", "docs/PROOF_OBLIGATIONS.md", "docs/RELATED_WORK.md",
    "docs/RESEARCH_REVIEW.md", "docs/INDEPENDENT_RESEARCH_REVIEW.md", "docs/RELEASE_ARTIFACTS.md",
    "docs/NUMERICAL_IMPLEMENTATION_REVIEW.md", "docs/RESEARCH_PLAN.md", "docs/EXPERIMENT_PROTOCOL.md",
    "research/source-register.json", "research/research-contract-v1.json",
    "research/evaluation-freeze-v1.json", "research/protocols/evaluation-locked-v1.json",
    "research/protocols/evaluation-selection-v1.json", "research/protocols/validation-v1.json",
    "research/protocols/learning-and-evaluation-split-v1.json",
    "research/report/main.tex", "research/report/README.md",
    "research/report/build_report.py", "research/report/paper_figures.py",
    "research/report/generated/results.tex", "research/report/generated/evidence.tex",
    "output/pdf/figures/paper-layout-provenance.json",
    "output/pdf/figures/paper-outcomes-1.pdf", "output/pdf/figures/paper-outcomes-1.png",
    "output/pdf/figures/paper-outcomes-2.pdf", "output/pdf/figures/paper-outcomes-2.png",
    "output/pdf/figures/paper-effects.pdf", "output/pdf/figures/paper-effects.png",
    "learning/requirements.in", "learning/requirements.lock", "learning/models/parity-cases.json",
    "analysis/README.md", "analysis/evaluate.py", "analysis/statistics_core.py", "analysis/figures.py",
    "tools/analyze_verified.py",
)
ANALYSIS_FILES = ("analysis.json", "report.md", "method-regime-summary.csv",
                  "primary-effects.csv", "run-timing-summary.csv")
LEARNING_FILES = ("artifacts/learning/training-summary.json", "artifacts/learning/java-parity.json",
                  "artifacts/learning/labels/summary.json", "artifacts/learning/labels/generation-timing.json",
                  "artifacts/learning/labels/train.csv", "artifacts/learning/labels/validation.csv")
TEXT_SUFFIXES = {".json", ".csv", ".md", ".tex", ".txt", ".cff", ".lock", ".in", ".py"}
UNSAFE = tuple(re.compile(pattern, re.I) for pattern in (
    rb"-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----",
    rb"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\b",
    rb'"(?:password|clientSecret|apiKey|accessToken)"\s*:\s*"[^"\r\n]{8,}"',
    rb"(?<![A-Za-z0-9+.-])[A-Za-z]:(?:\\{1,2}|/)",
    rb"/(?:Users|home)/[^/\s\"']+/",
))


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def safe_name(value: str) -> str:
    """Validate an archive/source-relative POSIX name without normalizing it."""
    if (not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/-]*", value)
            or any(part in {"", ".", ".."} for part in value.split("/"))):
        raise ValueError("Unsafe relative artifact name")
    parts = PurePosixPath(value).parts
    if any(part in {".local", ".venv", "node_modules", "__pycache__", "validation-interrupted-before-freeze"} for part in parts):
        raise ValueError("Private/cache path is not a release artifact")
    if value.startswith("research/sources/"):
        raise ValueError("Source publications are not release artifacts")
    return value


def required(root: Path, name: str) -> Path:
    name = safe_name(name)
    target = root / name
    if not target.is_file():
        raise ValueError(f"Missing required release input: {name}")
    resolved = target.resolve()
    if not resolved.is_relative_to(root.resolve()) or any(part.is_symlink() for part in [target, *target.parents] if part != root.parent):
        raise ValueError(f"Symlink or escaped release input: {name}")
    return target


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field in release evidence")
        result[key] = value
    return result


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_object,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite release JSON")))


def scan_public_bytes(name: str, content: bytes) -> None:
    """Block recognizable credentials/local account paths without echoing them."""
    if any(pattern.search(content) for pattern in UNSAFE):
        raise ValueError(f"Potential private material in allowlisted file: {safe_name(name)} (review locally)")


def scan_file(name: str, path: Path) -> None:
    if path.suffix not in TEXT_SUFFIXES and name not in {"LICENSE", "NOTICE"}:
        return
    with path.open("rb") as stream:
        overlap = b""
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            scan_public_bytes(name, overlap + block)
            overlap = block[-512:]


def payload_shape(path: Path, name: str) -> None:
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rb") as stream:
        content = stream.read(64 * 1024 * 1024 + 1)
    if len(content) > 64 * 1024 * 1024:
        raise ValueError(f"Decoded payload exceeds 64 MiB: {name}")
    scan_public_bytes(name, content)
    value = json.loads(content.decode("utf-8-sig"), object_pairs_hook=unique_object,
                       parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite payload")))
    if not isinstance(value, dict):
        raise ValueError("Run payload must be a JSON object")


def read_manifest(root: Path, directory: str, stage: str) -> tuple[dict, dict[str, Path]]:
    """Require complete unique paired rows and hash every eligible payload.

    Synthetic miniature manifests are accepted here for boundary tests. The
    release planner separately requires the actual named frozen scientific cohort.
    """
    archive = read_json(required(root, directory + "/manifest.json"))
    if archive.get("schemaVersion") != "comparison-evidence-v1" or archive["protocol"]["stage"] != stage:
        raise ValueError("Wrong saved-evidence schema or stage")
    for field in ("sourceSha256", "protocolSha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", archive.get(field, "")):
            raise ValueError("Invalid evidence fingerprint")
    protocol = archive["protocol"]
    problems = [problem["id"] for problem in protocol["problems"]]
    methods = [method["id"] for method in protocol["methods"]]
    if len(problems) != len(set(problems)) or len(methods) != len(set(methods)):
        raise ValueError("Duplicate protocol identity")
    expected = {(problem, method, Fraction(tolerance)) for problem, method, tolerance
                in itertools.product(problems, methods, protocol["tolerances"])}
    actual = [(run["problemId"], run["methodId"], Fraction(run["tolerance"])) for run in archive["runs"]]
    if (not expected or len(actual) != len(expected) or set(actual) != expected
            or len({run["id"] for run in archive["runs"]}) != len(actual)):
        raise ValueError("Manifest must contain the complete unique paired cohort")
    files = {}
    for run in archive["runs"]:
        if run["status"] not in {"CERTIFIED", "BUDGET_EXHAUSTED", "CANCELLED", "NUMERICAL_FAILURE", "UNAVAILABLE"}:
            raise ValueError("Unsupported final outcome status")
        if run["status"] == "UNAVAILABLE":
            if run.get("evidenceFile") is not None or run.get("evidenceSha256") is not None or run.get("timings") != []:
                raise ValueError("Unavailable method cannot contain fabricated payloads or timings")
            continue
        relative = safe_name(run["evidenceFile"])
        if not re.fullmatch(r"runs/[A-Za-z0-9_-]+\.json(?:\.gz)?", relative):
            raise ValueError("Unexpected numerical payload name")
        name = directory + "/" + relative
        path = required(root, name)
        if name in files or digest(path) != run["evidenceSha256"]:
            raise ValueError("Duplicate or changed numerical evidence payload")
        payload_shape(path, name)
        files[name] = path
    return archive, files


def iter_csv(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or len(reader.fieldnames) != len(set(reader.fieldnames)):
            raise ValueError("Invalid CSV header")
        for row in reader:
            if None in row or None in row.values():
                raise ValueError("Malformed CSV evidence row")
            yield row


def csv_rows(path: Path) -> list[dict]:
    return list(iter_csv(path))


def check_csv(root: Path, directory: str, archive: dict) -> None:
    rows = csv_rows(required(root, directory + "/outcomes.csv"))
    indexed = {run["id"]: run for run in archive["runs"]}
    if len(rows) != len(indexed) or {row["run"] for row in rows} != set(indexed):
        raise ValueError("Outcome CSV does not retain the complete cohort")
    for row in rows:
        run = indexed[row["run"]]
        if (row["problem"], row["method"], row["status"], Fraction(row["tolerance"])) != (
                run["problemId"], run["methodId"], run["status"], Fraction(run["tolerance"])):
            raise ValueError("Outcome CSV disagrees with saved manifest")
    measurements = csv_rows(required(root, directory + "/measurements.csv"))
    if Counter(row["run"] for row in measurements) != Counter({run["id"]: len(run["timings"]) for run in archive["runs"] if run["timings"]}):
        raise ValueError("Timing CSV does not retain declared observations")
    for name in CSV_NAMES[2:]:
        if any(row["run"] not in indexed for row in iter_csv(required(root, directory + "/" + name))):
            raise ValueError("Diagnostic CSV refers to an unknown run")


def select_examples(archive: dict, selection: dict) -> list[str]:
    """Choose fixed first IDs and frozen validation-selected models, never statuses."""
    first = {min(problem["id"] for problem in archive["protocol"]["problems"]
                 if problem["id"].startswith("test-" + regime + "-")) for regime in REGIMES}
    methods = {selection["primaryDeterministicMethod"], *selection["primaryLearningMethodIds"],
               "random-seed-0", "alpha-grid", "published-pwl-core"}
    return sorted(run["id"] for run in archive["runs"] if run["problemId"] in first and run["methodId"] in methods)


def check_receipt(root: Path, name: str, archive: dict, *, final: bool) -> None:
    receipt = read_json(required(root, name))
    directory = "artifacts/evaluation" if final else "artifacts/validation"
    expected = {"manifestSha256": digest(required(root, directory + "/manifest.json")),
                "sourceSha256": archive["sourceSha256"], "protocolSha256": archive["protocolSha256"]}
    if any(receipt.get(key) != value for key, value in expected.items()):
        raise ValueError("Replay receipt does not identify the packaged evidence")
    if final and receipt.get("status") != "PASS":
        raise ValueError("Final saved replay must have an actual PASS receipt")
    if "status" in receipt and receipt["status"] != "PASS":
        raise ValueError("Replay receipt does not record success")
    eligible = sum(run["status"] != "UNAVAILABLE" for run in archive["runs"])
    if "verifiedRunCount" in receipt and receipt["verifiedRunCount"] != eligible:
        raise ValueError("Replay receipt has the wrong eligible run count")


def verify_analysis_inputs(root: Path, analysis: dict, archive: dict) -> None:
    """Match the frozen loader's base-file plus eligible-payload hash schema."""
    expected_names = {"manifest.json", *CSV_NAMES}
    expected_names.update(run["evidenceFile"] for run in archive["runs"] if run["status"] != "UNAVAILABLE")
    if set(analysis["inputHashes"]) != expected_names:
        raise ValueError("Analysis input fingerprint set omits or adds declared evidence")
    for relative, expected in analysis["inputHashes"].items():
        if digest(required(root, "artifacts/evaluation/" + safe_name(relative))) != expected:
            raise ValueError("Analyzed input bytes changed")


def verify_paper_layout(root: Path) -> None:
    """Refuse stale or synthetic presentation assets in a scientific release."""
    provenance = read_json(required(root, "output/pdf/figures/paper-layout-provenance.json"))
    if (provenance.get("syntheticFixture") is not False
            or provenance.get("scope") != "DISPLAY_ONLY_NO_STATISTICAL_RECOMPUTATION"):
        raise ValueError("Synthetic or unknown paper layout cannot enter release archives")
    for key, name in (("sourceAnalysisSha256", "artifacts/evaluation-analysis/analysis.json"),
                      ("rendererSha256", "research/report/paper_figures.py")):
        if provenance.get(key) != digest(required(root, name)):
            raise ValueError("Paper layout does not match the current saved analysis/renderer")
    originals = {name + "." + extension for name in FIGURE_NAMES for extension in ("png", "svg", "pdf")}
    outputs = {name + "." + extension for name in ("paper-outcomes-1", "paper-outcomes-2", "paper-effects")
               for extension in ("png", "pdf")}
    for key, expected_names, directory in (("originalFigureSha256", originals, "artifacts/evaluation-analysis/figures/"),
                                            ("outputSha256", outputs, "output/pdf/figures/")):
        if set(provenance.get(key, {})) != expected_names:
            raise ValueError("Paper layout provenance omits or adds figure files")
        for name, expected in provenance[key].items():
            if digest(required(root, directory + safe_name(name))) != expected:
                raise ValueError("Paper/original figure bytes differ from layout provenance")


def build_plan(root: Path, evaluation_receipt="artifacts/verification/evaluation-replay.json") -> dict:
    """Validate actual final inputs and return two explicit immutable file maps."""
    root = root.resolve()
    # Check final existence first; partial validation alone must never look releasable.
    required(root, "artifacts/evaluation/manifest.json")
    verify_final_replay(root=root, receipt=root / evaluation_receipt)
    evaluation, evaluation_payloads = read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")
    validation, validation_payloads = read_manifest(root, "artifacts/validation", "VALIDATION_ONLY")
    if evaluation["protocol"]["id"] != "evaluation-locked-v1" or validation["protocol"]["id"] != "validation-v1":
        raise ValueError("Publication requires actual named frozen protocols, not synthetic fixtures")
    for archive, name in ((evaluation, "evaluation-locked-v1"), (validation, "validation-v1")):
        if read_json(required(root, "research/protocols/" + name + ".json")) != archive["protocol"]:
            raise ValueError("Standalone frozen protocol differs from embedded evidence")
    bases = {problem["baseProblemId"] for problem in evaluation["protocol"]["problems"]}
    if len(bases) != 120 or any(len({problem["baseProblemId"] for problem in evaluation["protocol"]["problems"]
                                    if problem["id"].startswith("test-" + regime + "-")}) != 30 for regime in REGIMES):
        raise ValueError("Final evaluation must retain all 120 declared base problems")
    if len({problem["baseProblemId"] for problem in validation["protocol"]["problems"]}) != 24:
        raise ValueError("Validation must retain all 24 declared base problems")
    if any(len(archive["protocol"]["methods"]) != 32 for archive in (evaluation, validation)):
        raise ValueError("Publication must retain all 32 methods and seeds")
    for directory, archive in (("artifacts/evaluation", evaluation), ("artifacts/validation", validation)):
        check_csv(root, directory, archive)
    check_receipt(root, "artifacts/verification/validation-replay.json", validation, final=False)
    check_receipt(root, evaluation_receipt, evaluation, final=True)
    freeze = read_json(required(root, "research/evaluation-freeze-v1.json"))
    selection = read_json(required(root, "research/protocols/evaluation-selection-v1.json"))
    analysis = read_json(required(root, "artifacts/evaluation-analysis/analysis.json"))
    verify_paper_layout(root)
    if analysis.get("analysisVersion") != "analysis-v1" or analysis.get("protocolId") != evaluation["protocol"]["id"]:
        raise ValueError("Final saved analysis has the wrong version or protocol")
    for key in ("sourceSha256", "protocolSha256"):
        if freeze[key] != evaluation[key] or analysis["frozen" + key[0].upper() + key[1:]] != evaluation[key]:
            raise ValueError("Final freeze/analysis provenance differs from packaged evaluation")
    if analysis["freezeSha256"] != digest(required(root, "research/evaluation-freeze-v1.json")):
        raise ValueError("Analysis freeze bytes changed")
    if analysis["frozenSelection"] != freeze:
        raise ValueError("Analysis frozen selection changed")
    if analysis["referenceAudit"]["violations"]:
        raise ValueError("Qualified lower-bound violations block publication")
    verify_analysis_inputs(root, analysis, evaluation)
    if selection["validationManifestSha256"] != digest(required(root, "artifacts/validation/manifest.json")):
        raise ValueError("Selection did not use the packaged validation manifest")
    split_hash = digest(required(root, "research/protocols/learning-and-evaluation-split-v1.json"))
    if selection["baseSplitManifestSha256"] != split_hash:
        raise ValueError("Base split manifest changed after validation selection")
    for key in ("primaryDeterministicMethod", "primaryLearnedFamily", "primaryLearningMethodIds"):
        if freeze[key] != selection[key] or analysis[key] != selection[key]:
            raise ValueError("Primary policy differs from validation-frozen selection")
    for model in MODEL_NAMES:
        if selection["modelSha256"][model] != digest(required(root, "learning/models/" + model + ".json")):
            raise ValueError("Frozen learned model bytes changed")
    label_summary = read_json(required(root, "artifacts/learning/labels/summary.json"))
    training = read_json(required(root, "artifacts/learning/training-summary.json"))
    if training["manifestSha256"] != split_hash:
        raise ValueError("Training used a different base split manifest")
    if training["finalTestRowsLoaded"] != 0 or training["trainingGroups"] != 64 or training["validationGroups"] != 24:
        raise ValueError("Training split isolation does not match the declared design")
    for split, key in (("train", "trainingDataSha256"), ("validation", "validationDataSha256")):
        actual = digest(required(root, "artifacts/learning/labels/" + split + ".csv"))
        if label_summary["dataSha256"][split] != actual or training[key] != actual:
            raise ValueError("Learning label bytes differ from saved training evidence")
    names = set(SHARED_FILES) | {"learning/models/" + name + ".json" for name in MODEL_NAMES}
    names |= {"artifacts/evaluation-analysis/" + name for name in ANALYSIS_FILES}
    names |= {f"artifacts/evaluation-analysis/figures/{name}.{extension}"
              for name in FIGURE_NAMES for extension in ("png", "svg", "pdf")}
    names |= {"artifacts/verification/validation-replay.json", safe_name(evaluation_receipt)}
    for directory in ("artifacts/evaluation", "artifacts/validation"):
        names |= {directory + "/" + name for name in ("manifest.json", "summary.md", *CSV_NAMES)}
    full = {name: required(root, name) for name in sorted(names | set(LEARNING_FILES))}
    full.update(evaluation_payloads)
    full.update(validation_payloads)
    selected_ids = select_examples(evaluation, selection)
    selected_rows = [run for run in evaluation["runs"] if run["id"] in selected_ids]
    selected_files = {"artifacts/evaluation/" + run["evidenceFile"] for run in selected_rows if run["evidenceFile"] is not None}
    omitted_csv = {directory + "/" + name for directory in ("artifacts/evaluation", "artifacts/validation") for name in CSV_NAMES[2:]}
    curated = {name: path for name, path in full.items()
               if name not in set(evaluation_payloads) | set(validation_payloads) | omitted_csv | set(LEARNING_FILES)}
    curated.update({name: evaluation_payloads[name] for name in sorted(selected_files)})
    # Small aggregate learning reports are useful; raw labels remain full-bundle only.
    curated.update({name: full[name] for name in LEARNING_FILES if not name.endswith(".csv")})
    for name, path in full.items():
        scan_file(name, path)
    metadata = {"evaluationRuns": len(evaluation["runs"]), "validationRuns": len(validation["runs"]),
                "evaluationPayloads": len(evaluation_payloads), "validationPayloads": len(validation_payloads),
                "selectedRunIds": selected_ids, "curatedEvaluationPayloads": len(selected_files),
                "omittedCuratedFiles": sorted(set(full) - set(curated)),
                "sourceSha256": evaluation["sourceSha256"], "protocolSha256": evaluation["protocolSha256"]}
    return {"full": full, "curated": curated, "metadata": metadata}


def zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(safe_name(name), date_time=(1980, 1, 1, 0, 0, 0))
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def write_zip(path: Path, files: dict[str, Path], generated: dict[str, bytes]) -> None:
    """Stream sorted original bytes with fixed timestamps/modes and SHA256SUMS."""
    if set(files) & set(generated) or "SHA256SUMS" in files or "SHA256SUMS" in generated:
        raise ValueError("Duplicate/reserved archive entry")
    checksums = {}
    for name, source in files.items():
        safe_name(name)
        checksums[name] = digest(source)
    for name, content in generated.items():
        safe_name(name)
        scan_public_bytes(name, content)
        checksums[name] = hashlib.sha256(content).hexdigest()
    sums = "".join(checksums[name] + "  " + name + "\n" for name in sorted(checksums)).encode()
    members = dict(generated, SHA256SUMS=sums)
    raw = path.open("xb")
    try:
        with raw, zipfile.ZipFile(raw, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, allowZip64=True) as archive:
            for name in sorted(set(files) | set(members)):
                info = zip_info(name)
                # Explicit ZipInfo does not inherit ZipFile's compression level.
                info._compresslevel = 9
                if name in members:
                    archive.writestr(info, members[name])
                else:
                    with files[name].open("rb") as source, archive.open(info, "w", force_zip64=True) as output:
                        shutil.copyfileobj(source, output, length=1024 * 1024)
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None or set(archive.namelist()) != set(checksums) | {"SHA256SUMS"}:
                raise ValueError("ZIP verification failed")
            for name, expected in checksums.items():
                with archive.open(name) as stream:
                    if hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                        raise ValueError("Stored ZIP member hash mismatch")
    except BaseException:
        raw.close()
        path.unlink(missing_ok=True)
        raise


def readme(curated: bool, metadata: dict, revision: str | None) -> bytes:
    scope = "CURATED PARTIAL EVIDENCE" if curated else "COMPLETE EVALUATION AND VALIDATION DATA"
    details = ("This small portfolio bundle is NOT the complete raw numerical cohort. It retains complete evaluation/validation manifests, outcome/timing CSVs, final analysis and selected raw examples. The unchanged manifests intentionally reference omitted payloads. Full-cohort replay requires the data ZIP. Diagnostic/refinement CSVs and raw training labels omitted here remain in that full ZIP."
               if curated else "This bundle contains every declared evaluation/validation manifest row, CSV and individually hashed eligible run payload, including failures. Unavailable methods have explicit rows and no fabricated payload. It also contains saved analysis, training/validation labels/reports, frozen models, protocols and study source metadata. It excludes unrelated caches, source-paper PDFs and interrupted runs.")
    text = f"""# Fuzzy Dynamics Lab {VERSION}: {scope}

{details}

Intended source reference: https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/tree/v{VERSION}
Full data asset: fuzzy-dynamics-lab-v{VERSION}-data.zip on that release.
Source Git revision: {revision or 'not supplied; use the recorded source/build fingerprint below'}.
Source/build fingerprint: {metadata['sourceSha256']}
Protocol fingerprint: {metadata['protocolSha256']}

Declared evaluation rows: {metadata['evaluationRuns']}; validation rows: {metadata['validationRuns']}.
Full eligible evaluation payloads: {metadata['evaluationPayloads']}; validation payloads: {metadata['validationPayloads']}.
Curated eligible evaluation payloads: {metadata['curatedEvaluationPayloads']}.
CURATED_SELECTION.json records exact selected IDs and omissions. Selection uses the lexically first problem ID of each test regime and the validation-frozen primary comparator/models, random-seed-0, alpha grid and PWL core. It never consults final success or timing to choose examples. A selected unavailable row has no payload.

Every file except SHA256SUMS is listed in SHA256SUMS, hashed as stored. ZIP timestamps and permissions are fixed; original file bytes are unchanged. Check with sha256sum -c SHA256SUMS after extraction. Hashes establish byte consistency, not authentication, trustworthy timing, a numerical proof or independent peer review.

The package command does not run Java verification, analysis, training or model selection. Saved replay receipts identify externally completed replay observations. Run the documented source checks and saved-artifact replay for independent reproduction; the curated bundle cannot support full replay alone. The study PDF is a separate reviewed asset, not a source-paper PDF.

Membership is not probability. Certificates, qualified reference brackets, empirical results and scientific novelty have distinct scopes. Scalar references do not validate joint two-dimensional geometry. Failures, unavailable methods, finite precision, timing-subset limits, AI-assisted internal review and unresolved closest-literature full-text comparisons remain explicit in the accompanying study and method documents.
"""
    return text.encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "output/release")
    parser.add_argument("--evaluation-replay-receipt", default="artifacts/verification/evaluation-replay.json", help="Project-relative actual successful final replay receipt")
    parser.add_argument("--source-revision", help="Optional exact 40-hex Git revision corresponding to this release")
    parser.add_argument("--max-curated-mib", type=int, default=20)
    args = parser.parse_args()
    if args.source_revision and not re.fullmatch(r"[0-9a-f]{40}", args.source_revision):
        parser.error("--source-revision must be an exact lowercase Git commit hash")
    if args.max_curated_mib <= 0:
        parser.error("--max-curated-mib must be positive")
    plan = build_plan(args.root, args.evaluation_replay_receipt)
    output = args.output_dir.resolve()
    if any(source.resolve() == output or source.resolve().is_relative_to(output) for source in plan["full"].values()):
        raise ValueError("Archive output must not contain or overwrite input evidence")
    output.mkdir(parents=True, exist_ok=True)
    if (output / "SHA256SUMS").exists():
        raise ValueError("Refusing to overwrite existing release checksums; choose a fresh output directory")
    selection = dict(schemaVersion="release-curation-v1", version=VERSION,
                     curatedIsCompleteNumericalCohort=False, **plan["metadata"])
    metadata = json.dumps(selection, indent=2, sort_keys=True, allow_nan=False).encode() + b"\n"
    staged = []
    try:
        for key, suffix in (("full", "data"), ("curated", "evidence")):
            target = output / f"fuzzy-dynamics-lab-v{VERSION}-{suffix}.zip"
            temporary = target.with_suffix(".zip.part")
            if target.exists() or temporary.exists():
                raise ValueError(f"Refusing to overwrite an existing release archive: {target.name}")
            write_zip(temporary, plan[key], {"BUNDLE_README.md": readme(key == "curated", plan["metadata"], args.source_revision),
                                           "CURATED_SELECTION.json": metadata})
            staged.append((temporary, target))
            if key == "curated" and temporary.stat().st_size > args.max_curated_mib * 1024 * 1024:
                raise ValueError("Curated ZIP exceeds the explicit portfolio size budget; review selection instead of silently dropping files")
        for temporary, target in staged:
            # Same-directory hard link exposes verified bytes atomically and
            # refuses an existing target on both Windows and POSIX.
            os.link(temporary, target)
            temporary.unlink()
        checksums = "".join(digest(target) + "  " + target.name + "\n" for _, target in staged)
        with (output / "SHA256SUMS").open("x", encoding="ascii", newline="\n") as stream:
            stream.write(checksums)
        print(checksums, end="")
    finally:
        for temporary, _ in staged:
            if temporary.is_file():
                temporary.unlink()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"Release packaging refused: {error}", file=sys.stderr)
        raise SystemExit(1)
