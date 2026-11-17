"""Explicitly synthetic release-boundary fixtures; never read project outcomes."""

import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import package_release


def fixture(root, *, path="runs/fixture.json", status="CERTIFIED"):
    """A two-method miniature manifest, never the actual scientific cohort."""
    evidence = root / "artifacts/evaluation"
    (evidence / "runs").mkdir(parents=True, exist_ok=True)
    payload = b'{"syntheticFixture":true}\n'
    (evidence / "runs/fixture.json").write_bytes(payload)
    protocol = {"id": "explicitly-synthetic-fixture", "stage": "FROZEN_HELD_OUT",
                "problems": [{"id": "test-id-1d-000", "baseProblemId": "test-id-1d-000"}],
                "methods": [{"id": "maximum-bound"}, {"id": "published-pwl-core"}], "tolerances": ["0.1"]}
    available = {"id": "synthetic-available", "problemId": "test-id-1d-000",
                 "methodId": "maximum-bound", "tolerance": "0.1", "status": status,
                 "evidenceFile": path, "evidenceSha256": hashlib.sha256(payload).hexdigest(), "timings": [{}]}
    unavailable = dict(available, id="synthetic-unavailable", methodId="published-pwl-core",
                       status="UNAVAILABLE", evidenceFile=None, evidenceSha256=None, timings=[])
    manifest = {"schemaVersion": "comparison-evidence-v1", "protocol": protocol,
                "sourceSha256": "a" * 64, "protocolSha256": "b" * 64,
                "runs": [available, unavailable]}
    (evidence / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    return manifest


class PackagingBoundaryTest(unittest.TestCase):
    def test_paper_layout_requires_current_analysis_renderer_and_output_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def put(name, content):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
                return hashlib.sha256(content).hexdigest()
            analysis_hash = put("artifacts/evaluation-analysis/analysis.json", b'{"explicitSyntheticFixture":true}')
            renderer_hash = put("research/report/paper_figures.py", b"# Explicit synthetic source fixture\n")
            originals = {name + "." + extension: put("artifacts/evaluation-analysis/figures/" + name + "." + extension,
                         b"Explicit synthetic original figure bytes")
                         for name in package_release.FIGURE_NAMES for extension in ("pdf", "png", "svg")}
            outputs = {name + "." + extension: put("output/pdf/figures/" + name + "." + extension,
                       b"Explicit synthetic paper figure bytes")
                       for name in ("paper-outcomes-1", "paper-outcomes-2", "paper-effects") for extension in ("pdf", "png")}
            provenance = {"syntheticFixture": False, "scope": "DISPLAY_ONLY_NO_STATISTICAL_RECOMPUTATION",
                          "sourceAnalysisSha256": analysis_hash, "rendererSha256": renderer_hash,
                          "originalFigureSha256": originals, "outputSha256": outputs}
            path = root / "output/pdf/figures/paper-layout-provenance.json"
            path.write_text(json.dumps(provenance))
            package_release.verify_paper_layout(root)
            provenance["syntheticFixture"] = True
            path.write_text(json.dumps(provenance))
            with self.assertRaises(ValueError):
                package_release.verify_paper_layout(root)
            provenance["syntheticFixture"] = False
            path.write_text(json.dumps(provenance))
            (root / "output/pdf/figures/paper-effects.pdf").write_bytes(b"Changed synthetic bytes")
            with self.assertRaises(ValueError):
                package_release.verify_paper_layout(root)

    def test_real_analysis_hash_shape_includes_every_eligible_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest = fixture(root)
            directory = root / "artifacts/evaluation"
            for name in package_release.CSV_NAMES:
                (directory / name).write_bytes(b"synthetic,fixture\n1,true\n")
            names = {"manifest.json", *package_release.CSV_NAMES, "runs/fixture.json"}
            hashes = {name: hashlib.sha256((directory / name).read_bytes()).hexdigest() for name in names}
            package_release.verify_analysis_inputs(root, {"inputHashes": hashes}, manifest)
            missing = {name: value for name, value in hashes.items() if name != "runs/fixture.json"}
            with self.assertRaisesRegex(ValueError, "fingerprint"):
                package_release.verify_analysis_inputs(root, {"inputHashes": missing}, manifest)
            with self.assertRaisesRegex(ValueError, "fingerprint"):
                package_release.verify_analysis_inputs(root, {"inputHashes": dict(hashes, **{"runs/extra.json": "a" * 64})}, manifest)

    def test_traversal_and_absolute_names_are_rejected(self):
        for value in ("../secret", "/etc/passwd", "C:/private/key", "runs\\file.json", "x/../../key", "./file", "x//file"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                package_release.safe_name(value)

    def test_manifest_checks_complete_cohort_hashes_and_unavailable_rows(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest = fixture(root)
            parsed, files = package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")
            self.assertEqual(2, len(parsed["runs"]))
            self.assertEqual(["artifacts/evaluation/runs/fixture.json"], sorted(files))
            manifest["runs"].pop()
            (root / "artifacts/evaluation/manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "complete"):
                package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")

    def test_manifest_traversal_and_missing_payload_are_not_published(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture(root, path="../private.json")
            with self.assertRaises(ValueError):
                package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")

    def test_changed_payload_or_fabricated_unavailable_timing_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture(root)
            (root / "artifacts/evaluation/runs/fixture.json").write_bytes(b"{}")
            with self.assertRaisesRegex(ValueError, "changed"):
                package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")
            manifest = fixture(root)
            manifest["runs"][1]["timings"] = [{}]
            (root / "artifacts/evaluation/manifest.json").write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "Unavailable"):
                package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")
            fixture(root)
            (root / "artifacts/evaluation/runs/fixture.json").unlink()
            with self.assertRaises(ValueError):
                package_release.read_manifest(root, "artifacts/evaluation", "FROZEN_HELD_OUT")

    def test_real_release_plan_refuses_missing_final_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "Missing required"):
                package_release.build_plan(Path(temporary))

    def test_direct_packaging_requires_actual_java_replay_preflight(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "artifacts/evaluation").mkdir(parents=True)
            (root / "research").mkdir()
            (root / "artifacts/evaluation/manifest.json").write_text("{}")
            (root / "research/evaluation-freeze-v1.json").write_text("{}")
            with self.assertRaisesRegex(ValueError, "evaluation-replay.json"):
                package_release.build_plan(root)

    def test_curated_examples_depend_on_identifiers_not_final_success(self):
        protocol = {"problems": [{"id": f"test-{regime}-{index:03d}", "baseProblemId": f"test-{regime}-{index:03d}"}
                                 for regime in package_release.REGIMES for index in (0, 1)]}
        rows = [{"id": f"{problem['id']}-{method}", "problemId": problem["id"], "methodId": method,
                 "status": "BUDGET_EXHAUSTED"} for problem in protocol["problems"]
                for method in ("maximum-bound", "ridge", "random-seed-0", "alpha-grid", "published-pwl-core")]
        archive = {"protocol": protocol, "runs": rows}
        selection = {"primaryDeterministicMethod": "maximum-bound", "primaryLearningMethodIds": ["ridge"]}
        first = package_release.select_examples(archive, selection)
        for row in rows:
            row["status"] = "CERTIFIED" if "001" in row["id"] else "NUMERICAL_FAILURE"
        self.assertEqual(first, package_release.select_examples(archive, selection))
        self.assertTrue(all("000" in identifier for identifier in first))
        self.assertTrue(any("random-seed-0" in identifier for identifier in first))

    def test_zip_bytes_are_deterministic_and_checksums_cover_contents(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = root / "source.txt"
            payload.write_bytes(b"synthetic public fixture\n")
            entries = {"data/fixture.txt": payload}
            first, second = root / "one.zip", root / "two.zip"
            package_release.write_zip(first, entries, {"README.md": b"Synthetic test only.\n"})
            payload.touch()
            package_release.write_zip(second, entries, {"README.md": b"Synthetic test only.\n"})
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                sums = archive.read("SHA256SUMS").decode()
                self.assertIn(hashlib.sha256(payload.read_bytes()).hexdigest() + "  data/fixture.txt", sums)
                self.assertEqual((1980, 1, 1, 0, 0, 0), archive.getinfo("data/fixture.txt").date_time)

    def test_rooted_windows_paths_are_generic_and_public_urls_remain_allowed(self):
        for value in (b'C:/Workspace/fixture/data.json', rb'D:\Workspace\fixture\data.json'):
            with self.assertRaises(ValueError):
                package_release.scan_public_bytes("fixture.json", value)
        package_release.scan_public_bytes("fixture.json", b'https://example.com/figures/report.pdf')

    def test_private_paths_or_credential_material_fail_without_echoing_values(self):
        value = b"-----BEGIN OPENSSH PRIVATE KEY-----"
        with self.assertRaises(ValueError) as caught:
            package_release.scan_public_bytes("fixture.json", value)
        self.assertNotIn(value.decode(), str(caught.exception))

    def test_existing_archive_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "retained.zip"
            path.write_bytes(b"previously reviewed bytes")
            with self.assertRaises(FileExistsError):
                package_release.write_zip(path, {}, {"README.md": b"Synthetic test only.\n"})
            self.assertEqual(b"previously reviewed bytes", path.read_bytes())


if __name__ == "__main__":
    unittest.main()
