"""Synthetic 32-method layout mapping; no final observations are loaded."""

import copy
import unittest

import paper_figures


def synthetic_analysis():
    methods = ["uniform", "maximum-bound", "next-step", "predicted-gain", "ridge"]
    methods += [f"mlp-seed-{seed}" for seed in (11, 23, 37, 51, 71)]
    methods += [f"random-seed-{seed}" for seed in range(20)]
    methods += ["alpha-grid", "published-pwl-core"]
    summaries = []
    for index, method in enumerate(methods):
        for regime_index, regime in enumerate(paper_figures.REGIMES):
            unavailable = method == "published-pwl-core" and regime.endswith("2d")
            certified = 0 if unavailable else (index + regime_index) % 31
            remaining = 0 if unavailable else 30 - certified
            cancelled = min(1, remaining)
            numerical = min(1, remaining - cancelled)
            budget = remaining - cancelled - numerical
            counts = {"CERTIFIED": certified, "BUDGET_EXHAUSTED": budget,
                      "CANCELLED": cancelled, "NUMERICAL_FAILURE": numerical,
                      "UNAVAILABLE": 30 if unavailable else 0}
            summaries.append({"method": method, "regime": regime, "requested": 30,
                "eligible": 0 if unavailable else 30, "certified": certified,
                "budgetExhausted": budget, "otherFailures": cancelled + numerical,
                "unavailable": counts["UNAVAILABLE"], "baseProblems": 30,
                "baseWeightedSuccessFraction": certified / 30,
                "statuses": {key: value for key, value in counts.items() if value}})
    effects = []
    for endpoint in paper_figures.ENDPOINTS:
        for index, scope in enumerate(paper_figures.SCOPES):
            missing = endpoint == "conditionalTimeRatio" and scope == "ood-2d"
            estimate = None if missing else (0.1 + index / 100 if endpoint == "certificationDifference" else 0.8 + index / 20)
            ci = None if missing else [estimate - 0.05, estimate + 0.08]
            effects.append({"endpoint": endpoint, "scope": scope, "estimate": estimate, "ci95": ci,
                            "baseProblems": 120 if scope == "all" else 30,
                            "validBootstrapReplicates": 0 if missing else 10000,
                            "bootstrapReplicates": 10000, "bothCertifiedPairs": 0 if missing else 12,
                            "declaredPairs": 120})
    return {"analysisVersion": "analysis-v1", "protocolId": "explicitly-synthetic-paper-layout",
            "declaredMethods": methods, "methodSummary": summaries, "effects": effects}


class PaperLayoutMappingTest(unittest.TestCase):
    def test_every_method_regime_status_and_saved_value_survives_two_panels(self):
        source = synthetic_analysis()
        before = copy.deepcopy(source)
        layout = paper_figures.layout_map(source)
        self.assertEqual([16, 16], [len(panel["methods"]) for panel in layout["outcomePanels"]])
        self.assertEqual(source["declaredMethods"], sum([panel["methods"] for panel in layout["outcomePanels"]], []))
        mapped = [cell for panel in layout["outcomePanels"] for cell in panel["cells"]]
        self.assertEqual(128, len(mapped))
        indexed = {(row["method"], row["regime"]): row for row in source["methodSummary"]}
        for cell in mapped:
            original = indexed[cell["method"], cell["regime"]]
            self.assertEqual(original["baseWeightedSuccessFraction"], cell["value"])
            self.assertEqual(original["statuses"], cell["statuses"])
            self.assertEqual(original["requested"], cell["requested"])
        self.assertEqual(before, source)

    def test_all_three_endpoints_keep_every_scope_exact_ci_and_unavailable_state(self):
        source = synthetic_analysis()
        panels = paper_figures.layout_map(source)["effectPanels"]
        self.assertEqual(list(paper_figures.ENDPOINTS), [panel["endpoint"] for panel in panels])
        original = {(row["endpoint"], row["scope"]): row for row in source["effects"]}
        for panel in panels:
            self.assertEqual(list(paper_figures.SCOPES), [row["scope"] for row in panel["rows"]])
            for row in panel["rows"]:
                self.assertEqual(original[panel["endpoint"], row["scope"]], row)
        missing = next(row for panel in panels for row in panel["rows"] if row["estimate"] is None)
        self.assertIsNone(missing["ci95"])
        self.assertEqual(0, missing["validBootstrapReplicates"])

    def test_missing_or_duplicate_cohort_cells_are_rejected(self):
        source = synthetic_analysis()
        source["methodSummary"].pop()
        with self.assertRaises(ValueError):
            paper_figures.layout_map(source)
        source = synthetic_analysis()
        source["effects"].append(copy.deepcopy(source["effects"][0]))
        with self.assertRaises(ValueError):
            paper_figures.layout_map(source)


if __name__ == "__main__":
    unittest.main()
