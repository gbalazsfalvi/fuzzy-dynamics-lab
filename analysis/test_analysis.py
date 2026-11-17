"""Hand-derived fixtures; no final evaluation data are loaded by these tests."""

from fractions import Fraction
from pathlib import Path
import importlib.util
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def row(base, method, status="CERTIFIED", seconds=2, variant="a", regime="id-1d", timed=True, images=10):
    return dict(run=f"{base}-{variant}-{method}", problem=f"{base}-{variant}", base=base,
                regime=regime, method=method, tolerance="0.1", status=status, reason=status,
                timingCohort=timed, totalSeconds=seconds, verificationSeconds=0.2,
                algorithmMapImages=images, algorithmExactPointEvaluations=0,
                maximumBound=Fraction(1, 10), timings=[])


class StatisticalContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = None
        if importlib.util.find_spec("statistics_core") is not None:
            import statistics_core
            cls.module = statistics_core

    def core(self):
        self.assertIsNotNone(self.module, "The frozen grouped estimator is not implemented yet")
        return self.module

    def test_shared_seed_draws_are_global_across_regimes(self):
        # Opposite seed effects in two one-base regimes cancel in EVERY shared draw.
        data = [row("b1", "det", regime="id-1d"), row("b2", "det", regime="ood-1d")]
        for base, regime, statuses in [("b1", "id-1d", ["CERTIFIED", "BUDGET_EXHAUSTED"]),
                                        ("b2", "ood-1d", ["BUDGET_EXHAUSTED", "CERTIFIED"])]:
            data += [row(base, method, status, regime=regime)
                     for method, status in zip(["m1", "m2"], statuses)]
        effects = self.core().effects(data, "det", ["m1", "m2"], replicates=100, seed=4)
        primary = next(e for e in effects if e["scope"] == "all" and e["endpoint"] == "certificationDifference")
        self.assertEqual(primary["estimate"], -0.5)
        self.assertEqual(primary["ci95"], [-0.5, -0.5])
        self.assertEqual(primary["baseProblems"], 2)

    def test_variant_rows_are_equal_weighted_inside_whole_base(self):
        # One failed base has three variants; one successful base has one: rate is 1/2, not 1/4.
        data = []
        for base, variants, status in [("b1", ["a", "b", "c"], "BUDGET_EXHAUSTED"),
                                        ("b2", ["a"], "CERTIFIED")]:
            for variant in variants:
                data += [row(base, "det", "BUDGET_EXHAUSTED", variant=variant),
                         row(base, "learn", status, variant=variant)]
        effect = self.core().effects(data, "det", ["learn"], replicates=200, seed=1)[0]
        self.assertEqual(effect["estimate"], 0.5)
        self.assertEqual(effect["ci95"], [0.0, 1.0])

    def test_runtime_is_conditional_and_excludes_noncohort_observations(self):
        data = [row("a", "det", seconds=4), row("a", "learn", seconds=2),
                row("b", "det", "BUDGET_EXHAUSTED", seconds=100), row("b", "learn", seconds=1),
                row("c", "det", seconds=1, timed=False), row("c", "learn", seconds=100, timed=False)]
        effects = self.core().effects(data, "det", ["learn"], replicates=100, seed=3)
        result = next(e for e in effects if e["scope"] == "all" and e["endpoint"] == "conditionalTimeRatio")
        self.assertEqual(result["estimate"], 0.5)
        self.assertEqual(result["bothCertifiedPairs"], 1)
        self.assertEqual(result["declaredPairs"], 2)
        self.assertEqual(result["baseProblems"], 2)

    def test_all_failed_ratios_are_unavailable_and_completion_stays_zero(self):
        data = [row("a", "det", "BUDGET_EXHAUSTED"), row("a", "learn", "NUMERICAL_FAILURE")]
        effects = self.core().effects(data, "det", ["learn"], replicates=100, seed=9)
        ratio = next(e for e in effects if e["scope"] == "all" and e["endpoint"] == "conditionalTimeRatio")
        self.assertIsNone(ratio["estimate"])
        self.assertIsNone(ratio["ci95"])
        self.assertEqual(ratio["validBootstrapReplicates"], 0)
        curve = self.core().completion_curve(data)
        self.assertEqual(curve["fractions"], [0.0, 0.0])
        self.assertEqual(curve["eligibleBases"], 1)

    def test_completion_keeps_failed_denominator_and_equal_base_weights(self):
        data = [row("a", "m", seconds=1), row("b", "m", "BUDGET_EXHAUSTED", seconds=2),
                row("c", "m", "UNAVAILABLE", seconds=None)]
        curve = self.core().completion_curve(data)
        self.assertEqual(curve["fractions"][-1], 0.5)
        self.assertEqual(curve["unavailableRows"], 1)
        self.assertEqual(curve["eligibleBases"], 2)
        self.assertEqual(self.core().completion_curve([])["times"], [])

    def test_exact_reference_lower_decides_violation_not_upper(self):
        rows = [dict(run="r1", quality="VERIFIED_ENCLOSURE", lower="1/10", upper="1/2", certificate="0.1"),
                dict(run="r2", quality="VERIFIED_ENCLOSURE", lower="100000000000000000000000000001/1000000000000000000000000000000", upper="1", certificate="0.1"),
                dict(run="r3", quality="EMPIRICAL_GRID", lower="1", upper="1", certificate="0.1")]
        audit = self.core().reference_audit(rows)
        self.assertEqual(audit["violations"], ["r2"])
        self.assertEqual(audit["upperOnlyExceedances"], 1)
        self.assertEqual(audit["unqualifiedRows"], 1)

    def test_break_even_rejects_successful_subset_and_incomplete_cost(self):
        data = [row("a", "det", seconds=4), row("a", "learn", seconds=2)]
        cost = dict(totalSeconds=10, complete=True, timingComparable=True)
        self.assertEqual(self.core().break_even(data, "det", ["learn"], cost)["repeatedSolves"], 5)
        data += [row("b", "det", "BUDGET_EXHAUSTED"), row("b", "learn")]
        self.assertIsNone(self.core().break_even(data, "det", ["learn"], cost)["repeatedSolves"])
        self.assertIsNone(self.core().break_even(data[:2], "det", ["learn"], {})["repeatedSolves"])

    def test_missing_paired_method_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "paired"):
            self.core().effects([row("a", "det")], "det", ["learn"], replicates=10, seed=1)


if __name__ == "__main__":
    unittest.main()
