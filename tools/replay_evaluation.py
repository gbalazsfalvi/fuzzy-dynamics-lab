"""Run real saved Java evaluation verification and record its observed receipt.

No success receipt is created from exit status alone. The exact success marker,
eligible count and unchanged manifest/freeze/protocol bytes are all required.
Logs and retired receipts stay in ignored artifacts/verification, never in source.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
from fractions import Fraction
import itertools
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

try:
    from . import analyze_verified as guard
except ImportError:
    import analyze_verified as guard

ROOT = Path(__file__).resolve().parents[1]
MARKER = re.compile(r"Verified ([0-9]+) saved numerical runs, all declared outcomes, and diagnostics\.")


def command_for(*, windows: bool) -> list[str]:
    if windows:
        return ["cmd.exe", "/d", "/c", "gradlew.bat", "--console=plain", "verifyEvaluation"]
    return ["./gradlew", "--console=plain", "verifyEvaluation"]


def completed_inputs(root: Path) -> tuple[dict, dict[str, str], int]:
    """Check exact frozen identity and full declared cohort before starting Java."""
    names = ("artifacts/evaluation/manifest.json", "research/evaluation-freeze-v1.json",
             "research/protocols/evaluation-locked-v1.json")
    paths = {name: guard.input_path(root, Path(name), "") for name in names}
    archive, freeze, locked = (guard.read_json(paths[name]) for name in names)
    if archive.get("schemaVersion") != "comparison-evidence-v1":
        raise ValueError("Wrong evaluation schema")
    if (locked.get("id") != "evaluation-locked-v1" or locked.get("stage") != "FROZEN_HELD_OUT"
            or guard.exact_json(archive.get("protocol")) != guard.exact_json(locked)):
        raise ValueError("Saved evaluation differs from the exact frozen protocol")
    for field in ("sourceSha256", "protocolSha256", "protocolFileSha256"):
        if not re.fullmatch(r"[a-f0-9]{64}", str(freeze.get(field, ""))):
            raise ValueError("Missing frozen replay provenance")
    if guard.digest(paths[names[2]]) != freeze["protocolFileSha256"]:
        raise ValueError("Frozen protocol file bytes changed")
    if any(archive.get(field) != freeze[field] for field in ("sourceSha256", "protocolSha256")):
        raise ValueError("Manifest and frozen replay provenance differ")
    problems = {problem["id"]: problem for problem in locked["problems"]}
    methods = [method["id"] for method in locked["methods"]]
    if not problems or len(problems) != len(locked["problems"]) or not methods or len(methods) != len(set(methods)):
        raise ValueError("Duplicate or empty frozen replay cohort")
    expected = {(problem, method, Fraction(tolerance)) for problem, method, tolerance
                in itertools.product(problems, methods, locked["tolerances"])}
    rows = archive["runs"]
    actual = [(row["problemId"], row["methodId"], Fraction(row["tolerance"])) for row in rows]
    if len(actual) != len(expected) or set(actual) != expected or len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Saved evaluation is not the complete unique declared cohort")
    eligible = 0
    for row in rows:
        if row["status"] not in {"CERTIFIED", "BUDGET_EXHAUSTED", "CANCELLED", "NUMERICAL_FAILURE", "UNAVAILABLE"}:
            raise ValueError("Unsupported saved evaluation outcome")
        if row["status"] == "UNAVAILABLE":
            if row.get("evidenceFile") is not None or row.get("evidenceSha256") is not None or row.get("timings") != []:
                raise ValueError("Unavailable row carries fabricated evidence")
            continue
        relative = row.get("evidenceFile")
        if not isinstance(relative, str) or not re.fullmatch(r"runs/[A-Za-z0-9_-]+\.json(?:\.gz)?", relative):
            raise ValueError("Unsafe numerical payload path")
        guard.input_path(root, paths[names[0]].parent / relative, "")
        if not re.fullmatch(r"[a-f0-9]{64}", str(row.get("evidenceSha256", ""))):
            raise ValueError("Missing numerical payload fingerprint")
        problem = problems[row["problemId"]]
        if type(problem.get("timingCohort")) is not bool:
            raise ValueError("Missing frozen timing flag")
        count = locked["timingRepetitions"] if problem["timingCohort"] else 1
        if not isinstance(row.get("timings"), list) or len(row["timings"]) != count:
            raise ValueError("Saved timing cohort is incomplete")
        eligible += 1
    if eligible <= 0:
        raise ValueError("There is no eligible numerical payload to replay")
    return archive, {name: guard.digest(path) for name, path in paths.items()}, eligible


def run_replay(root: Path = ROOT) -> dict:
    """Stream one actual verifier run and publish a receipt only after all checks."""
    root = root.resolve()
    directory = root / "artifacts/verification"
    if not directory.resolve().is_relative_to(root) or any(path.is_symlink() for path in (directory, *directory.parents)):
        raise ValueError("Verification output is outside its explicit project directory")
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / ".evaluation-replay.lock"
    # An exclusive lock prevents competing runs from retiring each other's receipt.
    lock_stream = lock.open("x", encoding="ascii")
    active = directory / "evaluation-replay.json"
    identity = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + uuid.uuid4().hex[:8]
    pending = directory / ("evaluation-replay." + identity + ".json.tmp")
    try:
        lock_stream.write(identity)
        lock_stream.flush()
        if active.exists() or active.is_symlink():
            guard.input_path(root, active, "")
            active.rename(directory / ("evaluation-replay.previous." + identity + ".json"))
        # Even a preflight failure leaves no stale active PASS receipt.
        archive, before, eligible = completed_inputs(root)
        windows = os.name == "nt"
        wrapper = guard.input_path(root, Path("gradlew.bat" if windows else "gradlew"), "")
        if not windows and not os.access(wrapper, os.X_OK):
            raise ValueError("The Unix Gradle wrapper must be executable")
        command = command_for(windows=windows)
        log_path = directory / ("evaluation-replay." + identity + ".log")
        observed = []
        with log_path.open("x", encoding="utf-8", newline="\n") as log:
            process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       text=True, encoding="utf-8", errors="replace", bufsize=1)
            try:
                if process.stdout is None:
                    raise ValueError("Verifier output could not be captured")
                for line in process.stdout:
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    log.write(line)
                    marker = MARKER.fullmatch(line.rstrip("\r\n"))
                    if marker:
                        observed.append(int(marker.group(1)))
                exit_code = process.wait()
            finally:
                if process.stdout is not None:
                    process.stdout.close()
            log.flush()
        after = {name: guard.digest(guard.input_path(root, Path(name), "")) for name in before}
        if after != before:
            raise ValueError("Manifest, freeze or locked protocol bytes changed during replay")
        if exit_code != 0 or observed != [eligible]:
            raise ValueError("Java replay did not exit successfully with the exact expected verified count")
        receipt = {
            "schemaVersion": "java-evaluation-replay-v1", "status": "PASS",
            "manifestSha256": before["artifacts/evaluation/manifest.json"],
            "sourceSha256": archive["sourceSha256"], "protocolSha256": archive["protocolSha256"],
            "verifiedRunCount": eligible, "verifiedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "command": command, "exitCode": exit_code, "logSha256": guard.digest(log_path),
        }
        with pending.open("x", encoding="utf-8", newline="\n") as output:
            output.write(json.dumps(receipt, indent=2, sort_keys=True) + "\n")
            output.flush()
            os.fsync(output.fileno())
        if active.exists():
            raise ValueError("An active receipt appeared during the owned verification run")
        os.replace(pending, active)
        return receipt
    finally:
        if pending.exists():
            pending.unlink()
        lock_stream.close()
        lock.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    receipt = run_replay(args.root)
    print(json.dumps({"receipt": "artifacts/verification/evaluation-replay.json",
                      "status": receipt["status"], "verifiedRunCount": receipt["verifiedRunCount"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(f"Evaluation replay refused: {error}", file=sys.stderr)
        raise SystemExit(1)
