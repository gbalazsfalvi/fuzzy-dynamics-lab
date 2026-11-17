"""Small formatter boundary checks; never read final evaluation artifacts."""

from pathlib import Path
import json
import tempfile
import unittest
from unittest.mock import patch

import build_report


class ReportBoundaryTest(unittest.TestCase):
    def test_unavailable_conditional_estimate_is_not_zero(self):
        self.assertEqual("unavailable", build_report.number(None))
        self.assertEqual("unavailable", build_report.interval(None))
        self.assertEqual("0", build_report.number(0))

    def test_nonfinite_or_malformed_estimates_are_rejected(self):
        for value in (float("nan"), float("inf"), True, "0.1"):
            with self.assertRaises(ValueError):
                build_report.number(value)
        with self.assertRaises(ValueError):
            build_report.interval([1, 0])

    def test_artifact_text_cannot_inject_tex_commands(self):
        value = build_report.tex(r"x_1 & 50% \input{private}")
        self.assertIn(r"x\_1 \& 50\%", value)
        self.assertIn(r"\textbackslash{}input\{private\}", value)
        self.assertNotIn(r"\input{", value)

    def test_relative_payload_path_cannot_escape_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "safe.json").write_text("{}")
            self.assertEqual(root / "safe.json", build_report.safe_evidence(root, "safe.json"))
            with self.assertRaises(ValueError):
                build_report.safe_evidence(root, "../outside.json")

    def test_synthetic_layout_input_cannot_become_a_final_study(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            analysis = root / "analysis.json"
            manifest = root / "manifest.json"
            freeze = root / "freeze.json"
            analysis.write_text(json.dumps({"analysisVersion": "analysis-v1"}))
            manifest.write_text(json.dumps({"schemaVersion": "comparison-evidence-v1",
                "protocol": {"stage": "FROZEN_HELD_OUT", "id": "synthetic-layout-fixture"}}))
            freeze.write_text("{}")
            with self.assertRaisesRegex(ValueError, "not a layout fixture"):
                build_report.validate(analysis, manifest, freeze)

    def test_unavailable_manifest_row_has_no_payload_or_timing_to_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            row = {"status": "UNAVAILABLE", "evidenceFile": None,
                   "evidenceSha256": None, "timings": []}
            build_report.verify_payloads(Path(directory), [row])
            with self.assertRaisesRegex(ValueError, "Unavailable"):
                build_report.verify_payloads(Path(directory), [dict(row, evidenceSha256="a" * 64)])

    def test_available_manifest_row_cannot_omit_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                build_report.verify_payloads(Path(directory), [{"status": "CERTIFIED",
                    "evidenceFile": None, "evidenceSha256": None, "timings": []}])

    def test_direct_report_requires_actual_matching_java_replay_before_rendering(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "artifacts/evaluation").mkdir(parents=True)
            (root / "research").mkdir()
            analysis = root / "analysis.json"
            manifest = root / "artifacts/evaluation/manifest.json"
            freeze = root / "research/evaluation-freeze-v1.json"
            analysis.write_text(json.dumps({"analysisVersion": "analysis-v1"}))
            manifest.write_text(json.dumps({"schemaVersion": "comparison-evidence-v1",
                "protocol": {"stage": "FROZEN_HELD_OUT", "id": "evaluation-locked-v1"}}))
            freeze.write_text("{}")
            with patch.object(build_report, "ROOT", root), self.assertRaisesRegex(ValueError, "evaluation-replay.json"):
                build_report.validate(analysis, manifest, freeze)


if __name__ == "__main__":
    unittest.main()
