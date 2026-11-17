"""Synthetic provenance fixtures only; never execute analysis or read final data."""

import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
from types import SimpleNamespace
from unittest.mock import patch

import analyze_verified


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fixture(root):
    """A small explicitly synthetic protocol with one unavailable method."""
    protocol = {"id": "evaluation-locked-v1", "stage": "FROZEN_HELD_OUT", "syntheticFixture": True,
                "tolerances": ["0.1"], "timingRepetitions": 5,
                "problems": [{"id": "test-id-1d-000", "baseProblemId": "base-one", "timingCohort": True},
                             {"id": "test-id-2d-000", "baseProblemId": "base-two", "timingCohort": False}],
                "methods": [{"id": "maximum-bound"}, {"id": "published-pwl-core"}]}
    protocol_file = root / "research/protocols/evaluation-locked-v1.json"
    write(protocol_file, protocol)
    rows = []
    for problem in protocol["problems"]:
        for method in protocol["methods"]:
            unavailable = method["id"] == "published-pwl-core" and "2d" in problem["id"]
            identifier = problem["id"] + "-" + method["id"]
            relative = "runs/" + identifier + ".json"
            payload = root / "artifacts/evaluation" / relative
            write(payload, {"syntheticFixture": True})
            rows.append({"id": identifier, "problemId": problem["id"], "methodId": method["id"],
                         "tolerance": "0.1", "status": "UNAVAILABLE" if unavailable else "CERTIFIED",
                         "evidenceFile": None if unavailable else relative,
                         "evidenceSha256": None if unavailable else sha(payload),
                         "timings": [] if unavailable else [{}] * (5 if problem["timingCohort"] else 1)})
    manifest = {"schemaVersion": "comparison-evidence-v1", "sourceSha256": "a" * 64,
                "protocolSha256": "b" * 64, "protocol": protocol, "runs": rows}
    manifest_file = root / "artifacts/evaluation/manifest.json"
    write(manifest_file, manifest)
    freeze = {"sourceSha256": "a" * 64, "protocolSha256": "b" * 64,
              "protocolFileSha256": sha(protocol_file)}
    write(root / "research/evaluation-freeze-v1.json", freeze)
    receipt = {"status": "PASS", "manifestSha256": sha(manifest_file),
               "sourceSha256": "a" * 64, "protocolSha256": "b" * 64, "verifiedRunCount": 3}
    receipt_file = root / "artifacts/verification/evaluation-replay.json"
    write(receipt_file, receipt)
    return manifest, receipt


class VerifiedAnalysisBoundaryTest(unittest.TestCase):
    def test_verified_alias_cannot_select_an_unverified_sibling_manifest_for_analysis(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            actual = root / "artifacts/evaluation/manifest.json"
            alias = actual.with_name("verified-copy.json")
            alias.write_bytes(actual.read_bytes())
            actual.write_text('{"unverifiedSibling":true}')
            with self.assertRaisesRegex(ValueError, "manifest.json"):
                analyze_verified.validate(root=root, manifest=alias)

    def test_matching_exact_protocol_and_real_shape_receipt_pass_preflight_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            result = analyze_verified.validate(root=root)
            self.assertEqual(3, result["verifiedRunCount"])
            self.assertEqual(4, result["declaredRunCount"])

    def test_missing_receipt_does_not_infer_replay_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            (root / "artifacts/verification/evaluation-replay.json").unlink()
            with self.assertRaisesRegex(ValueError, "Missing"):
                analyze_verified.validate(root=root)

    def test_grouping_or_timing_mutation_is_rejected_even_with_matching_labels(self):
        for key, value in (("baseProblemId", "a-different-group"), ("timingCohort", False), ("timingCohort", 1)):
            with self.subTest(key=key), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                manifest, receipt = fixture(root)
                manifest["protocol"]["problems"][0][key] = value
                write(root / "artifacts/evaluation/manifest.json", manifest)
                receipt["manifestSha256"] = sha(root / "artifacts/evaluation/manifest.json")
                write(root / "artifacts/verification/evaluation-replay.json", receipt)
                with self.assertRaisesRegex(ValueError, "exact frozen protocol"):
                    analyze_verified.validate(root=root)

    def test_wrong_raw_protocol_hash_and_wrong_verified_count_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            _, receipt = fixture(root)
            receipt["verifiedRunCount"] = 4
            write(root / "artifacts/verification/evaluation-replay.json", receipt)
            with self.assertRaisesRegex(ValueError, "count"):
                analyze_verified.validate(root=root)
            fixture(root)
            path = root / "research/protocols/evaluation-locked-v1.json"
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaisesRegex(ValueError, "protocol file"):
                analyze_verified.validate(root=root)

    def test_duplicate_json_and_paths_outside_root_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            (root / "artifacts/verification/evaluation-replay.json").write_text('{"status":"PASS","status":"FAIL"}')
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                analyze_verified.validate(root=root)
            with self.assertRaisesRegex(ValueError, "outside"):
                analyze_verified.validate(root=root, manifest=root.parent / "outside.json")

    def test_cli_verify_only_cannot_invoke_analysis(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            with patch("sys.argv", ["analyze_verified.py", "--root", str(root), "--verify-only"]), \
                    patch.object(analyze_verified.subprocess, "run") as execute, redirect_stdout(io.StringIO()):
                self.assertEqual(0, analyze_verified.main())
                execute.assert_not_called()

    def test_cli_calls_unchanged_analyzer_with_the_same_interpreter_after_preflight(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture(root)
            source = root / "analysis/evaluate.py"
            source.parent.mkdir()
            source.write_text("# Synthetic command-selection fixture; never executed.\n")
            with patch("sys.argv", ["analyze_verified.py", "--root", str(root)]), \
                    patch.object(analyze_verified.subprocess, "run", return_value=SimpleNamespace(returncode=0)) as execute, \
                    redirect_stdout(io.StringIO()):
                self.assertEqual(0, analyze_verified.main())
                command = execute.call_args.args[0]
                self.assertEqual(analyze_verified.sys.executable, command[0])
                self.assertEqual(str(source.resolve()), command[1])
                self.assertIn(str((root / "artifacts/evaluation").resolve()), command)


if __name__ == "__main__":
    unittest.main()
