"""Require an actual Java replay receipt and exact frozen protocol before analysis.

This unfrozen publication preflight does not modify frozen statistical code,
replay numerical geometry, invent receipts or authenticate operator assertions.
Without --verify-only it invokes the unchanged analysis/evaluate.py afterward.
"""

from __future__ import annotations

import argparse
from decimal import Decimal
from fractions import Fraction
import hashlib
import itertools
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def input_path(root: Path, value: Path | None, default: str) -> Path:
    """Allow explicit paths only inside the selected project, with no symlinks."""
    path = Path(value) if value is not None else Path(default)
    if ".." in path.parts:
        raise ValueError("Input path traverses outside its explicit location")
    if not path.is_absolute():
        path = root / path
    resolved = path.resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError("Input path is outside the selected project")
    if any(part.is_symlink() for part in (path, *path.parents) if part != root.parent):
        raise ValueError("Symlinked input is not a verified publication source")
    if not path.is_file():
        raise ValueError(f"Missing required verified-analysis input: {path.name}")
    return resolved


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key in verified-analysis input")
        result[key] = value
    return result


def read_json(path: Path) -> dict:
    """Compare JSON numbers exactly, rather than collapsing distinct floats."""
    value = json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_object,
                       parse_float=Decimal,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Nonfinite JSON")))
    if not isinstance(value, dict):
        raise ValueError("Verified-analysis input must be a JSON object")
    return value


def exact_json(value):
    """Preserve JSON types, including true versus 1, with exact decimal numbers."""
    if isinstance(value, dict):
        return ("object", tuple((key, exact_json(item)) for key, item in sorted(value.items())))
    if isinstance(value, list):
        return ("array", tuple(exact_json(item) for item in value))
    if type(value) is bool:
        return ("boolean", value)
    if isinstance(value, (int, Decimal)):
        return ("number", Decimal(value))
    if value is None:
        return ("null", None)
    return ("string", value)


def validate(*, root: Path = ROOT, manifest: Path | None = None,
             freeze: Path | None = None, receipt: Path | None = None,
             protocol: Path | None = None) -> dict:
    """Check raw-byte provenance, exact embedded protocol and complete cohort.

    The receipt is an observation recorded by the operator after a successful
    Java saved replay. This check cannot establish its authenticity. Payload
    hashes and numerical/analysis validation remain downstream requirements.
    """
    root = root.resolve()
    manifest_path = input_path(root, manifest, "artifacts/evaluation/manifest.json")
    if manifest_path.name != "manifest.json":
        raise ValueError("Verified analysis requires the actual manifest.json basename")
    freeze_path = input_path(root, freeze, "research/evaluation-freeze-v1.json")
    receipt_path = input_path(root, receipt, "artifacts/verification/evaluation-replay.json")
    protocol_path = input_path(root, protocol, "research/protocols/evaluation-locked-v1.json")
    archive, frozen, replay = (read_json(path) for path in (manifest_path, freeze_path, receipt_path))
    for field in ("sourceSha256", "protocolSha256", "protocolFileSha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", str(frozen.get(field, ""))):
            raise ValueError(f"Missing or malformed frozen {field}")
    if digest(protocol_path) != frozen["protocolFileSha256"]:
        raise ValueError("Raw frozen protocol file hash mismatch")
    locked = read_json(protocol_path)
    if exact_json(archive.get("protocol")) != exact_json(locked):
        raise ValueError("Embedded grouping/timing/cohort differs from exact frozen protocol")
    if locked.get("id") != "evaluation-locked-v1" or locked.get("stage") != "FROZEN_HELD_OUT":
        raise ValueError("The protocol is not the frozen held-out evaluation")
    if archive.get("schemaVersion") != "comparison-evidence-v1":
        raise ValueError("Unsupported final evidence schema")
    for field in ("sourceSha256", "protocolSha256"):
        if archive.get(field) != frozen[field] or replay.get(field) != frozen[field]:
            raise ValueError("Replay/manifest/freeze provenance mismatch")
    if replay.get("status") != "PASS" or replay.get("manifestSha256") != digest(manifest_path):
        raise ValueError("A matching actual successful Java replay receipt is required")
    problems = {problem["id"]: problem for problem in locked["problems"]}
    methods = [method["id"] for method in locked["methods"]]
    if (not problems or len(problems) != len(locked["problems"])
            or len(methods) != len(set(methods)) or not methods):
        raise ValueError("Duplicate or empty frozen problem/method cohort")
    grouping = {}
    for problem in problems.values():
        if type(problem.get("timingCohort")) is not bool:
            raise ValueError("Frozen timing-cohort flag must be explicit")
        base = problem["baseProblemId"]
        if base in grouping and grouping[base] != problem["timingCohort"]:
            raise ValueError("A base problem has inconsistent timing flags")
        grouping[base] = problem["timingCohort"]
    rows = archive["runs"]
    if not isinstance(rows, list) or not rows:
        raise ValueError("Final run cohort must be a nonempty JSON array")
    expected = {(problem, method, Fraction(tolerance)) for problem, method, tolerance
                in itertools.product(problems, methods, locked["tolerances"])}
    actual = [(row["problemId"], row["methodId"], Fraction(row["tolerance"])) for row in rows]
    if len(actual) != len(expected) or set(actual) != expected or len({row["id"] for row in rows}) != len(actual):
        raise ValueError("Final manifest does not preserve the complete unique frozen cohort")
    eligible = 0
    for row in rows:
        if not isinstance(row.get("timings"), list):
            raise ValueError("Run timing evidence must be a JSON array")
        if row["status"] not in {"CERTIFIED", "BUDGET_EXHAUSTED", "CANCELLED", "NUMERICAL_FAILURE", "UNAVAILABLE"}:
            raise ValueError("Unsupported final outcome status")
        if row["status"] == "UNAVAILABLE":
            if row.get("evidenceFile") is not None or row.get("evidenceSha256") is not None or row.get("timings") != []:
                raise ValueError("Unavailable result cannot carry payload or timing evidence")
            continue
        relative = row.get("evidenceFile")
        if not isinstance(relative, str) or not re.fullmatch(r"runs/[a-zA-Z0-9_-]+\.json(?:\.gz)?", relative):
            raise ValueError("Unsafe numerical evidence path")
        input_path(root, manifest_path.parent / relative, "")
        if not re.fullmatch(r"[0-9a-f]{64}", str(row.get("evidenceSha256", ""))):
            raise ValueError("Malformed numerical payload fingerprint")
        repetitions = locked["timingRepetitions"] if problems[row["problemId"]]["timingCohort"] else 1
        if len(row["timings"]) != repetitions:
            raise ValueError("Run does not preserve the exact frozen timing design")
        eligible += 1
    if type(replay.get("verifiedRunCount")) is not int or replay["verifiedRunCount"] != eligible:
        raise ValueError("Successful replay eligible count differs from the final manifest")
    return {"status": "PASS", "declaredRunCount": len(rows), "verifiedRunCount": eligible,
            "manifestSha256": digest(manifest_path), "sourceSha256": frozen["sourceSha256"],
            "protocolSha256": frozen["protocolSha256"], "protocolFileSha256": frozen["protocolFileSha256"]}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--freeze", type=Path)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--protocol", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--output", type=Path, help="Analysis output directory, relative to --root when not absolute")
    parser.add_argument("--training-cost", type=Path, help="Optional explicit complete/comparable training cost JSON")
    args = parser.parse_args()
    root = args.root.resolve()
    result = validate(root=root, manifest=args.manifest, freeze=args.freeze, receipt=args.receipt, protocol=args.protocol)
    print(json.dumps(result, sort_keys=True))
    if args.verify_only:
        return 0
    manifest = input_path(root, args.manifest, "artifacts/evaluation/manifest.json")
    freeze = input_path(root, args.freeze, "research/evaluation-freeze-v1.json")
    analysis = input_path(root, None, "analysis/evaluate.py")
    output = args.output if args.output is not None else Path("artifacts/evaluation-analysis")
    if not output.is_absolute():
        output = root / output
    output = output.resolve()
    if not output.is_relative_to(root):
        raise ValueError("Analysis output is outside the selected project")
    command = [sys.executable, str(analysis), "--input", str(manifest.parent), "--freeze", str(freeze), "--output", str(output)]
    if args.training_cost:
        command += ["--training-cost", str(input_path(root, args.training_cost, ""))]
    return subprocess.run(command, cwd=root, check=False).returncode


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, OSError, TypeError) as error:
        print(f"Verified analysis refused: {error}", file=sys.stderr)
        raise SystemExit(1)
