"""Tests protect training rows, scaler isolation, and exported inference parity."""
import csv
import importlib.util
from pathlib import Path
import tempfile
import unittest


def trainer():
    spec = importlib.util.spec_from_file_location("train_ranker", Path(__file__).with_name("train_ranker.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TrainingTest(unittest.TestCase):
    def test_closed_form_ridge_has_no_iterative_solver_count(self):
        from types import SimpleNamespace
        module = trainer()
        self.assertIsNone(module.iteration_count(SimpleNamespace(n_iter_=None)))
        self.assertEqual(12, module.iteration_count(SimpleNamespace(n_iter_=12)))

    def test_rejects_a_final_test_group_in_the_training_csv(self):
        module = trainer()
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.csv"
            path.write_text("baseProblemId,path,split,f,signedGain\nhidden,root,train,0.5,0.2\n")
            with self.assertRaisesRegex(ValueError, "group"):
                module.load_rows(path, ["f"], {"allowed"}, "train")

    def test_accepts_finite_features_but_rejects_nonfinite_targets(self):
        module = trainer()
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.csv"
            path.write_text("baseProblemId,path,split,f,signedGain\nallowed,root,train,0.5,-0.125\n")
            features, gains, groups = module.load_rows(path, ["f"], {"allowed"}, "train")
            self.assertEqual([[0.5]], features.tolist())
            self.assertEqual([-0.125], gains.tolist())
            self.assertEqual(["allowed"], groups)
            path.write_text("baseProblemId,path,split,f,signedGain\nallowed,root,train,0.5,nan\n")
            with self.assertRaisesRegex(ValueError, "finite"):
                module.load_rows(path, ["f"], {"allowed"}, "train")


if __name__ == "__main__":
    unittest.main()
