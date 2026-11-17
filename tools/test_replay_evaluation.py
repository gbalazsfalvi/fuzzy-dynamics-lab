"""Synthetic subprocess fixtures only; these tests never run Gradle or a solver."""

import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import replay_evaluation
from test_analyze_verified import fixture


class FakeProcess:
    def __init__(self, text, code=0, after=None):
        self.stdout = io.StringIO(text)
        self.code, self.after = code, after

    def wait(self):
        if self.after:
            self.after()
        return self.code


def prepare(root):
    fixture(root)
    for name in ("gradlew", "gradlew.bat"):
        path = root / name
        path.write_text("# Synthetic test fixture, never executed.\n")
        path.chmod(path.stat().st_mode | 0o111)


class ReplayReceiptTest(unittest.TestCase):
    def test_success_observes_process_and_atomically_records_actual_count(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prepare(root)
            process = FakeProcess("Synthetic progress only\nVerified 3 saved numerical runs, all declared outcomes, and diagnostics.\n")
            with patch.object(replay_evaluation.subprocess, "Popen", return_value=process) as start, patch("sys.stdout", new=io.StringIO()):
                receipt = replay_evaluation.run_replay(root)
            self.assertEqual("PASS", receipt["status"])
            self.assertEqual(3, receipt["verifiedRunCount"])
            self.assertEqual(0, receipt["exitCode"])
            self.assertEqual(receipt, json.loads((root / "artifacts/verification/evaluation-replay.json").read_text()))
            self.assertIn("verifyEvaluation", start.call_args.args[0])
            self.assertNotIn("shell", start.call_args.kwargs)
            self.assertEqual(1, len(list((root / "artifacts/verification").glob("evaluation-replay.previous.*.json"))))

    def test_exit_failure_marker_absence_and_wrong_count_cannot_leave_pass(self):
        cases = [(1, "Verified 3 saved numerical runs, all declared outcomes, and diagnostics.\n"),
                 (0, "Synthetic output without a success marker\n"),
                 (0, "Verified 4 saved numerical runs, all declared outcomes, and diagnostics.\n")]
        for code, text in cases:
            with self.subTest(code=code, text=text), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                prepare(root)
                with patch.object(replay_evaluation.subprocess, "Popen", return_value=FakeProcess(text, code)), \
                        patch("sys.stdout", new=io.StringIO()), self.assertRaises(ValueError):
                    replay_evaluation.run_replay(root)
                self.assertFalse((root / "artifacts/verification/evaluation-replay.json").exists())
                self.assertTrue(list((root / "artifacts/verification").glob("evaluation-replay.previous.*.json")))

    def test_changed_manifest_freeze_or_protocol_during_replay_cannot_record_pass(self):
        for name in ("artifacts/evaluation/manifest.json", "research/evaluation-freeze-v1.json",
                     "research/protocols/evaluation-locked-v1.json"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                prepare(root)
                def mutate():
                    path = root / name
                    path.write_bytes(path.read_bytes() + b" ")
                process = FakeProcess("Verified 3 saved numerical runs, all declared outcomes, and diagnostics.\n", after=mutate)
                with patch.object(replay_evaluation.subprocess, "Popen", return_value=process), \
                        patch("sys.stdout", new=io.StringIO()), self.assertRaisesRegex(ValueError, "changed"):
                    replay_evaluation.run_replay(root)
                self.assertFalse((root / "artifacts/verification/evaluation-replay.json").exists())

    def test_incomplete_cohort_does_not_start_subprocess_and_retires_prior_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prepare(root)
            manifest = root / "artifacts/evaluation/manifest.json"
            value = json.loads(manifest.read_text())
            value["runs"].pop()
            manifest.write_text(json.dumps(value))
            with patch.object(replay_evaluation.subprocess, "Popen") as start, self.assertRaises(ValueError):
                replay_evaluation.run_replay(root)
            start.assert_not_called()
            self.assertFalse((root / "artifacts/verification/evaluation-replay.json").exists())

    def test_windows_command_uses_cmd_without_a_shell_string(self):
        self.assertEqual(["cmd.exe", "/d", "/c", "gradlew.bat", "--console=plain", "verifyEvaluation"],
                         replay_evaluation.command_for(windows=True))
        self.assertEqual(["./gradlew", "--console=plain", "verifyEvaluation"], replay_evaluation.command_for(windows=False))


if __name__ == "__main__":
    unittest.main()
