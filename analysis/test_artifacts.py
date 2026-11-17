"""Synthetic saved-artifact integration fixtures, never real evaluation outputs."""

import csv
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

HEADERS = {
    "outcomes.csv": "run problem method tolerance status reason maximumBound geometryElements medianTotalNanoseconds algorithmMapImages algorithmLipschitzBounds algorithmMembershipRanges algorithmExactPointEvaluations verificationMapImages verificationExactPointEvaluations configurationSha256".split(),
    "measurements.csv": "run repetition orderIndex solveNanoseconds verificationNanoseconds totalNanoseconds".split(),
    "alpha-diagnostics.csv": "run time alpha status hausdorff referenceIntervals approximationElements".split(),
    "reference-errors.csv": "run time quality distanceToStaircase referenceError lower upper certificate".split(),
    "refinement-curves.csv": "run refinement currentBound bestBound activeCells selection".split(),
}


def fixture(directory, failed=False):
    """Small complete declared cohort: 4 regimes, 2 methods, one base each."""
    directory.mkdir(parents=True)
    (directory / "runs").mkdir()
    protocol = dict(stage="FROZEN_HELD_OUT", id="synthetic-fixture-not-final-data", timingRepetitions=5,
                    warmupRepetitions=1, precision=24, maxRefinements=512, tolerances=["0.1"],
                    methods=[dict(id="maximum-bound", kind="CAE"), dict(id="ridge", kind="CAE")], problems=[])
    archive = dict(schemaVersion="comparison-evidence-v1", protocolSha256="a"*64, sourceSha256="b"*64,
                   generatedAtUtc="2026-11-17T00:00:00Z", protocol=protocol, environment={"timedScope": "synthetic fixture"}, runs=[])
    records = {name: [] for name in HEADERS}
    for index, regime in enumerate(["id-1d", "id-2d", "ood-1d", "ood-2d"]):
        problem = f"test-{regime}-000"
        protocol["problems"].append(dict(id=problem, baseProblemId=problem, timingCohort=index < 2,
                                          qualifiedReference=False, definition=dict(horizon=2)))
        for method in ["maximum-bound", "ridge"]:
            run = f"{problem}-{method}"
            status = "BUDGET_EXHAUSTED" if failed else "CERTIFIED"
            timings = [dict(repetition=rep, orderIndex=0, solveNanoseconds=900, verificationNanoseconds=100,
                            totalNanoseconds=1000) for rep in range(5 if index < 2 else 1)]
            payload = b'{"syntheticTestFixture": true}\n'
            relative = f"runs/{run}.json"
            (directory / relative).write_bytes(payload)
            archive["runs"].append(dict(id=run, problemId=problem, methodId=method, tolerance="0.1",
                status=status, reason="synthetic-fixture", maximumBound="0.1" if not failed else "0.2",
                geometrySize=3, configurationSha256="c"*64,
                algorithmIntervalWork=dict(mapImages=10, lipschitzBounds=10, membershipRanges=3),
                algorithmExactPointEvaluations=0,
                verificationWork=dict(intervalWork=dict(mapImages=10), exactPointEvaluations=0),
                timings=timings, diagnostics=dict(cuts=[], referenceErrors=[]), evidenceFile=relative,
                evidenceSha256=hashlib.sha256(payload).hexdigest()))
            records["outcomes.csv"].append(dict(zip(HEADERS["outcomes.csv"],
                [run, problem, method, "0.1", status, "synthetic-fixture", "0.1" if not failed else "0.2",
                 3, 1000, 10, 10, 3, 0, 10, 0, "c"*64])))
            records["measurements.csv"] += [dict(run=run, **timing) for timing in timings]
    (directory / "manifest.json").write_text(json.dumps(archive), encoding="utf-8")
    for name, header in HEADERS.items():
        with (directory / name).open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=header)
            writer.writeheader()
            writer.writerows(records[name])
    freeze = dict(primaryDeterministicMethod="maximum-bound", primaryLearnedFamily="ridge",
                  primaryLearningMethodIds=["ridge"], protocolSha256="a"*64, sourceSha256="b"*64,
                  modelHashes={"ridge": "d"*64})
    freeze_path = directory.parent / "freeze.json"
    freeze_path.write_text(json.dumps(freeze), encoding="utf-8")
    return freeze_path


def compress_fixture_payloads(directory, replacement=None):
    """Replace manifest pointers with gzip files hashed as stored compressed bytes."""
    manifest_path = directory / "manifest.json"
    archive = json.loads(manifest_path.read_text(encoding="utf-8"))
    for run in archive["runs"]:
        original = directory / run["evidenceFile"]
        payload = replacement if replacement is not None else original.read_bytes()
        compressed = gzip.compress(payload, mtime=0)
        relative = run["evidenceFile"] + ".gz"
        (directory / relative).write_bytes(compressed)
        run["evidenceFile"] = relative
        run["evidenceSha256"] = hashlib.sha256(compressed).hexdigest()
    manifest_path.write_text(json.dumps(archive), encoding="utf-8")
    return archive


class ArtifactContract(unittest.TestCase):
    def module(self):
        self.assertIsNotNone(importlib.util.find_spec("evaluate"), "Saved-evidence analyzer is not implemented")
        import evaluate
        return evaluate

    def test_loader_preserves_raw_rows_and_excludes_noncohort_timings(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            bundle = self.module().load_bundle(folder, freeze)
            self.assertEqual(len(bundle["rows"]), 8)
            self.assertEqual(sum(row["timingCohort"] for row in bundle["rows"]), 4)
            self.assertEqual(len(bundle["measurements"]), 24)
            self.assertEqual(bundle["rows"][0]["totalSeconds"], 0.000001)

    def test_missing_failure_row_and_tampered_csv_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder, failed=True)
            target = folder / "outcomes.csv"
            content = target.read_text(encoding="utf-8")
            target.write_text("\n".join(content.splitlines()[:-1])+"\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "cohort|outcome"):
                self.module().load_bundle(folder, freeze)
            target.write_text(content.replace("BUDGET_EXHAUSTED", "CERTIFIED", 1), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "manifest|outcome"):
                self.module().load_bundle(folder, freeze)

    def test_payload_hash_and_cross_regime_grouping_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            payload = next((folder / "runs").iterdir())
            payload.write_text("changed", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "hash|digest"):
                self.module().load_bundle(folder, freeze)
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            path = folder / "manifest.json"
            manifest = json.loads(path.read_text())
            manifest["protocol"]["problems"][1]["baseProblemId"] = manifest["protocol"]["problems"][0]["baseProblemId"]
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "regime"):
                self.module().load_bundle(folder, freeze)

    def test_all_failure_end_to_end_writes_standalone_figures_without_changing_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder, failed=True)
            before = {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in folder.rglob("*") if path.is_file()}
            out = Path(temp) / "report"
            result = self.module().analyze(folder, freeze, out, replicates=100, seed=2)
            self.assertEqual(result["referenceAudit"]["violations"], [])
            self.assertTrue((out / "analysis.json").is_file())
            self.assertEqual(len(list((out / "figures").glob("*.png"))), 5)
            self.assertEqual(len(list((out / "figures").glob("*.svg"))), 5)
            self.assertEqual(len(list((out / "figures").glob("*.pdf"))), 5)
            self.assertIn("conditional", (out / "report.md").read_text(encoding="utf-8").lower())
            self.assertEqual(before, {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in before})

    def test_output_inside_input_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            with self.assertRaisesRegex(ValueError, "output|Output"):
                self.module().analyze(folder, freeze, folder / "nested", replicates=10, seed=1)

    def test_frozen_analysis_hash_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            document = json.loads(freeze.read_text())
            document["analysisSha256"] = {"analysis/evaluate.py": "0"*64}
            freeze.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "analysis.*hash|hash.*analysis"):
                self.module().load_bundle(folder, freeze)

    def test_compressed_payload_is_decoded_after_verifying_stored_byte_hash(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            archive = compress_fixture_payloads(folder)
            bundle = self.module().load_bundle(folder, freeze)
            self.assertEqual(len(bundle["rows"]), 8)
            relative = archive["runs"][0]["evidenceFile"]
            self.assertEqual(bundle["inputHashes"][relative], archive["runs"][0]["evidenceSha256"])
            (folder / relative).write_bytes(gzip.compress(b'{"changed": true}', mtime=0))
            with self.assertRaisesRegex(ValueError, "hash|digest"):
                self.module().load_bundle(folder, freeze)

    def test_compressed_invalid_json_is_rejected_even_with_matching_hash(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            compress_fixture_payloads(folder, replacement=b'{"unterminated":')
            with self.assertRaises(json.JSONDecodeError):
                self.module().load_bundle(folder, freeze)

    def test_decoded_payload_limit_is_enforced_before_parsing(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp) / "synthetic"
            freeze = fixture(folder)
            compress_fixture_payloads(folder, replacement=json.dumps({"oversized": "x" * 1024}).encode())
            module = self.module()
            with patch.object(module, "MAX_PAYLOAD_BYTES", 128, create=True):
                with self.assertRaisesRegex(ValueError, "exceeds|size|limit"):
                    module.load_bundle(folder, freeze)


if __name__ == "__main__":
    unittest.main()
