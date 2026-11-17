"""Frozen paired estimators; functions consume normalized saved rows, never models.

Problem clusters and globally shared model-seed draws are the resampling units.
Timing repetitions are summarized within a run before entering this module.
"""

from collections import Counter, defaultdict
from fractions import Fraction
import math
import warnings

import numpy as np


def paired(rows, comparator, learned):
    """Return every declared learned/comparator pair; reject omitted or duplicate rows."""
    methods = [comparator, *learned]
    if not learned or len(set(methods)) != len(methods):
        raise ValueError("Primary paired methods must be nonempty and distinct")
    groups = defaultdict(dict)
    for row in rows:
        if row["method"] in methods:
            key = (row["problem"], row["tolerance"])
            if row["method"] in groups[key]:
                raise ValueError("Duplicate paired row")
            groups[key][row["method"]] = row
    result = []
    for group in groups.values():
        if set(group) != set(methods):
            raise ValueError("Incomplete paired cohort")
        det = group[comparator]
        for seed, method in enumerate(learned):
            candidate = group[method]
            if any(candidate[key] != det[key] for key in ["base", "regime", "timingCohort"]):
                raise ValueError("Inconsistent paired problem grouping")
            result.append((det, candidate, seed))
    return result


def _matrices(pairs, seeds, endpoint):
    cells = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    counts = defaultdict(Counter)
    for det, candidate, seed in pairs:
        if endpoint == "conditionalTimeRatio" and not det["timingCohort"]:
            continue
        regime, base = det["regime"], det["base"]
        values = cells[regime][base][seed]
        counts[regime]["declaredPairs"] += 1
        both = det["status"] == candidate["status"] == "CERTIFIED"
        counts[regime]["bothCertifiedPairs"] += int(both)
        if endpoint == "certificationDifference":
            values.append(float(candidate["status"] == "CERTIFIED") - float(det["status"] == "CERTIFIED"))
        else:
            key = "totalSeconds" if endpoint == "conditionalTimeRatio" else "algorithmMapImages"
            numerator, denominator = candidate[key], det[key]
            if both and numerator is not None and denominator is not None and numerator > 0 and denominator > 0:
                values.append(math.log(numerator / denominator))
                counts[regime]["usablePairs"] += 1
    matrices = {}
    for regime, bases in sorted(cells.items()):
        matrix = np.full((len(bases), seeds), np.nan, dtype=float)
        for index, base in enumerate(sorted(bases)):
            for seed in range(seeds):
                values = bases[base][seed]
                if values:
                    value = float(np.mean(values))
                    matrix[index, seed] = value if endpoint == "certificationDifference" else math.exp(value)
        matrices[regime] = matrix
    return matrices, counts


def _finite_median(array, axis=None):
    # An empty conditional sample is explicitly unavailable; warnings add no information.
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        return np.nanmedian(array, axis=axis)


def effects(rows, comparator, learned, replicates=10_000, seed=20261117):
    """Paired 95% percentile intervals with base clusters and one shared seed draw.

    The aggregate success effect equally weights regimes. Conditional ratios are
    medians over qualifying base/seed cells, not full-cohort speedup estimates.
    """
    if replicates < 1:
        raise ValueError("Bootstrap repetitions must be positive")
    pairs = paired(rows, comparator, learned)
    rng = np.random.default_rng(seed)
    output = []
    for endpoint in ["certificationDifference", "conditionalTimeRatio", "conditionalMapImageRatio"]:
        matrices, counts = _matrices(pairs, len(learned), endpoint)
        # Crucial: the same seed indices are used across ALL bases and regimes.
        seed_indices = rng.integers(0, len(learned), size=(replicates, len(learned)))
        draws = {}
        for regime, matrix in matrices.items():
            base_indices = rng.integers(0, len(matrix), size=(replicates, len(matrix)))
            draws[regime] = matrix[base_indices[:, :, None], seed_indices[:, None, :]]
        for scope in ["all", *matrices]:
            selected = list(matrices) if scope == "all" else [scope]
            if not selected:
                estimate, distribution = np.nan, np.full(replicates, np.nan)
            elif endpoint == "certificationDifference":
                estimate = float(np.mean([matrices[group].mean() for group in selected]))
                distribution = np.mean([draws[group].mean(axis=(1, 2)) for group in selected], axis=0)
            else:
                estimate = float(_finite_median(np.concatenate([matrices[group].ravel() for group in selected])))
                distribution = _finite_median(np.concatenate([draws[group].reshape(replicates, -1)
                                                               for group in selected], axis=1), axis=1)
            valid = distribution[np.isfinite(distribution)]
            interval = (np.quantile(valid, [0.025, 0.975]).tolist()
                        if len(valid) >= math.ceil(0.9 * replicates) else None)
            total = sum((counts[group] for group in selected), Counter())
            output.append(dict(endpoint=endpoint, scope=scope,
                               estimate=estimate if math.isfinite(estimate) else None,
                               ci95=interval, bootstrapReplicates=replicates,
                               validBootstrapReplicates=len(valid),
                               baseProblems=sum(len(matrices[group]) for group in selected),
                               modelSeeds=len(learned), declaredPairs=total["declaredPairs"],
                               bothCertifiedPairs=total["bothCertifiedPairs"],
                               usablePairs=total["usablePairs"],
                               interpretation=("learned minus deterministic; equal regime/base/seed weights"
                                               if endpoint == "certificationDifference" else
                                               "learned/deterministic, conditional on both certified and positive cost")))
    return output


def completion_curve(rows):
    """Observed completion fraction; failures retain weight, unavailable rows do not run."""
    cohort = [row for row in rows if row["timingCohort"]]
    eligible = [row for row in cohort if row["status"] != "UNAVAILABLE"]
    unavailable = len(cohort) - len(eligible)
    groups = defaultdict(list)
    for row in eligible:
        groups[row["base"]].append(row)
    if not eligible:
        return dict(times=[], fractions=[], eligibleBases=0, eligibleRows=0, unavailableRows=unavailable)
    times = sorted({0.0, *[row["totalSeconds"] for row in eligible if row["totalSeconds"] is not None]})
    fractions = []
    for time in times:
        fractions.append(float(np.mean([
            sum(row["status"] == "CERTIFIED" and row["totalSeconds"] is not None
                and row["totalSeconds"] <= time for row in group) / len(group)
            for group in groups.values()
        ])))
    return dict(times=times, fractions=fractions, eligibleBases=len(groups),
                eligibleRows=len(eligible), unavailableRows=unavailable)


def reference_audit(rows):
    """Compare exact lower endpoints; never promote sampled errors to certificates."""
    violations, upper_only, qualified, unqualified = [], 0, 0, 0
    for row in rows:
        if row["quality"] not in {"ANALYTIC", "VERIFIED_ENCLOSURE"}:
            unqualified += 1
            continue
        lower, upper, certificate = (Fraction(row[key]) for key in ["lower", "upper", "certificate"])
        if not 0 <= lower <= upper <= 1 or not 0 <= certificate <= 1:
            raise ValueError("Invalid exact reference bracket")
        qualified += 1
        if lower > certificate:
            violations.append(row["run"])
        elif upper > certificate:
            upper_only += 1
    return dict(qualifiedRows=qualified, unqualifiedRows=unqualified, violations=violations,
                upperOnlyExceedances=upper_only,
                interpretation="Only qualified lower > certificate is a violation; scalar references only")


def break_even(rows, comparator, learned, cost=None):
    """Restricted amortization for the entire predeclared timing cohort, never survivors."""
    unavailable = lambda reason: dict(repeatedSolves=None, reason=reason)
    if not cost or cost.get("complete") is not True or cost.get("timingComparable") is not True:
        return unavailable("Complete comparable training/label/preprocessing/selection cost is unavailable")
    total = cost.get("totalSeconds")
    if not isinstance(total, (int, float)) or not math.isfinite(total) or total < 0:
        raise ValueError("Training totalSeconds must be finite and nonnegative")
    pairs = [pair for pair in paired(rows, comparator, learned) if pair[0]["timingCohort"]]
    if not pairs:
        return unavailable("No predeclared timing cohort")
    cells = defaultdict(list)
    for det, candidate, seed in pairs:
        if det["status"] != "CERTIFIED" or candidate["status"] != "CERTIFIED":
            return unavailable("Not every declared timing-cohort primary pair certified")
        if any(row["totalSeconds"] is None or row["totalSeconds"] <= 0 for row in [det, candidate]):
            return unavailable("Missing or nonpositive declared timing-cohort duration")
        cells[(det["regime"], det["base"], seed)].append(det["totalSeconds"] - candidate["totalSeconds"])
    saving = float(np.mean([np.mean(values) for values in cells.values()]))
    if saving <= 0:
        return unavailable("Mean paired timing-cohort savings are not positive")
    return dict(repeatedSolves=math.ceil(total / saving), meanSavingsSeconds=saving,
                totalTrainingSeconds=total,
                reason="Scenario only: repeated solves distributed like the complete timing cohort; not a deployment forecast")


def method_summary(rows):
    """Preserve method seeds, eligibility, and every observed failure status."""
    groups = defaultdict(list)
    for row in rows:
        groups[(row["regime"], row["method"])].append(row)
    result = []
    for (regime, method), group in sorted(groups.items()):
        counts = Counter(row["status"] for row in group)
        eligible = len(group) - counts["UNAVAILABLE"]
        by_base = defaultdict(list)
        for row in group:
            by_base[row["base"]].append(row["status"] == "CERTIFIED")
        result.append(dict(regime=regime, method=method, requested=len(group), eligible=eligible,
                           baseProblems=len(by_base), certified=counts["CERTIFIED"],
                           budgetExhausted=counts["BUDGET_EXHAUSTED"], unavailable=counts["UNAVAILABLE"],
                           otherFailures=len(group)-counts["CERTIFIED"]-counts["BUDGET_EXHAUSTED"]-counts["UNAVAILABLE"],
                           successFractionRequested=counts["CERTIFIED"] / len(group),
                           successFractionEligible=counts["CERTIFIED"] / eligible if eligible else None,
                           baseWeightedSuccessFraction=float(np.mean([np.mean(v) for v in by_base.values()])),
                           timingBases=len({r["base"] for r in group if r["timingCohort"]}),
                           statuses=dict(sorted(counts.items()))))
    return result
