"""Contract checks for split manifests and training isolation."""
from pathlib import Path
import importlib.util
import unittest


class SplitManifestTest(unittest.TestCase):
    def test_all_base_problem_variants_stay_in_exactly_one_partition(self):
        spec = importlib.util.spec_from_file_location("freeze_manifest", Path(__file__).with_name("freeze_manifest.py"))
        self.assertIsNotNone(spec)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        manifest = module.build_manifest()
        self.assertEqual(208, len(manifest["problems"]))
        counts = {}
        identities = {}
        definitions = set()
        import json
        for problem in manifest["problems"]:
            split = problem["split"]
            counts[split] = counts.get(split, 0) + 1
            identity = problem["baseProblemId"]
            self.assertNotIn(identity, identities)
            identities[identity] = split
            canonical = json.dumps(problem["definition"], sort_keys=True)
            self.assertNotIn(canonical, definitions)
            definitions.add(canonical)
        self.assertEqual({"train": 64, "validation": 24,
                          "test-id-1d": 30, "test-id-2d": 30,
                          "test-ood-1d": 30, "test-ood-2d": 30}, counts)
        self.assertEqual(manifest, module.build_manifest())

    def test_validation_selection_rejects_an_incomplete_paired_cohort(self):
        spec = importlib.util.spec_from_file_location("freeze_evaluation", Path(__file__).with_name("freeze_evaluation.py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        partial = {"protocol": module.protocol("VALIDATION_ONLY"), "runs": [
            {"problemId": "validation-000", "methodId": name, "status": "CERTIFIED",
             "algorithmIntervalWork": {"mapImages": 10}}
            for name in ["uniform", "maximum-bound", "next-step", "predicted-gain", "ridge",
                         "mlp-seed-11", "mlp-seed-23", "mlp-seed-37", "mlp-seed-51", "mlp-seed-71"]]}
        with self.assertRaisesRegex(ValueError, "cohort"):
            module.select_on_validation(partial)


if __name__ == "__main__":
    unittest.main()
