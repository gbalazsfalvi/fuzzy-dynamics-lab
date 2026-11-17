"""Standalone publication figures from saved evidence; no solver/model calls."""

from fractions import Fraction
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from statistics_core import completion_curve, paired

REGIMES = ("id-1d", "id-2d", "ood-1d", "ood-2d")


def _save(figure, directory, name):
    for extension in ("png", "svg", "pdf"):
        figure.savefig(directory / f"{name}.{extension}", dpi=220, bbox_inches="tight",
                       metadata={"Creator": "Fuzzy Dynamics Lab frozen analysis-v1"} if extension == "pdf" else None)
    plt.close(figure)


def _empty(axis, message):
    axis.text(0.5, 0.5, message, ha="center", va="center", transform=axis.transAxes, wrap=True)


def render_figures(bundle, result, directory):
    """Write five qualified figures in PNG/SVG/PDF, including explicit empty panels."""
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9, "axes.titlesize": 11,
                         "axes.labelsize": 10, "svg.fonttype": "none", "pdf.fonttype": 42,
                         "axes.spines.top": False, "axes.spines.right": False})
    rows = bundle["rows"]
    methods = result["declaredMethods"]
    summary = {(row["method"], row["regime"]): row for row in result["methodSummary"]}
    matrix = np.full((len(methods), len(REGIMES)), np.nan)
    for y, method in enumerate(methods):
        for x, regime in enumerate(REGIMES):
            row = summary.get((method, regime))
            if row and row["eligible"]:
                matrix[y, x] = row["baseWeightedSuccessFraction"]
    fig, axis = plt.subplots(figsize=(10, max(4.5, len(methods) * 0.32)))
    color = plt.get_cmap("viridis").copy()
    color.set_bad("#ededed")
    display = axis.imshow(matrix, vmin=0, vmax=1, aspect="auto", cmap=color)
    axis.set_xticks(range(len(REGIMES)), REGIMES)
    axis.set_yticks(range(len(methods)), methods)
    for y, method in enumerate(methods):
        for x, regime in enumerate(REGIMES):
            row = summary.get((method, regime))
            value = matrix[y, x]
            label = "unavailable" if row and row["eligible"] == 0 else "absent" if row is None else f"{100*value:.0f}% ({row['certified']}/{row['requested']})"
            axis.text(x, y, label, ha="center", va="center", fontsize=8,
                      color="white" if np.isfinite(value) and value < .6 else "black")
    axis.set_title("Certification over the complete declared cohort\nBase-weighted fractions; labels also retain raw certified/requested counts", pad=14)
    fig.colorbar(display, ax=axis, fraction=.035, pad=.03, label="Certified fraction (complete requested cohort)")
    fig.tight_layout()
    _save(fig, directory, "outcome-heatmap")

    comparisons = paired(rows, result["primaryDeterministicMethod"], result["primaryLearningMethodIds"])
    fig, axes = plt.subplots(2, 2, figsize=(10, 8))
    palette = plt.get_cmap("tab10")
    for axis, regime in zip(axes.flat, REGIMES):
        group = [pair for pair in comparisons if pair[0]["regime"] == regime]
        extent = 1
        for det, learned, seed in group:
            x, y = det["algorithmMapImages"], learned["algorithmMapImages"]
            extent = max(extent, x, y)
            both = det["status"] == learned["status"] == "CERTIFIED"
            axis.scatter(x, y, s=23, marker="o" if both else "x", alpha=.65,
                         color=palette(seed), linewidths=.8)
        axis.plot([0, extent], [0, extent], color="#777777", lw=.8, ls="--")
        axis.set_xscale("symlog", linthresh=1)
        axis.set_yscale("symlog", linthresh=1)
        axis.set_title(f"{regime}: {len({pair[0]['base'] for pair in group})} bases")
        axis.set_xlabel(f"{result['primaryDeterministicMethod']}: algorithm map images")
        axis.set_ylabel("Learned: algorithm map images")
        if not group:
            _empty(axis, "No declared primary pairs")
    legend = [plt.Line2D([], [], marker="o", linestyle="", color=palette(i), label=method)
              for i, method in enumerate(result["primaryLearningMethodIds"])]
    legend += [plt.Line2D([], [], marker="x", linestyle="", color="#333333", label="At least one did not certify")]
    fig.legend(handles=legend, loc="lower center", ncol=min(3, len(legend)), frameon=False)
    fig.suptitle("Paired algorithm work, including failures\nShared CAE engine only; exact-point and verification work remain separate", y=.99)
    fig.tight_layout(rect=(0, .09, 1, .93))
    _save(fig, directory, "paired-algorithm-work")

    fig, axes = plt.subplots(2, 2, figsize=(13, 10))
    handles = {}
    for axis, regime in zip(axes.flat, REGIMES):
        bases = {row["base"] for row in rows if row["regime"] == regime and row["timingCohort"]}
        horizon = max([row["totalSeconds"] for row in rows if row["regime"] == regime
                       and row["timingCohort"] and row["totalSeconds"] is not None] or [1.0])
        for index, method in enumerate(methods):
            curve = completion_curve([row for row in rows if row["method"] == method and row["regime"] == regime])
            if not curve["times"]:
                continue
            if curve["times"][-1] < horizon:
                curve["times"].append(horizon)
                curve["fractions"].append(curve["fractions"][-1])
            random = method.startswith("random-seed-")
            primary = method in result["primaryLearningMethodIds"] or method == result["primaryDeterministicMethod"]
            line, = axis.step(curve["times"], curve["fractions"], where="post", label=method,
                              color="#888888" if random else palette(index % 10),
                              alpha=.35 if random else .9, lw=1.7 if primary else .8,
                              linestyle="--" if random else "-")
            handles[method] = line
        axis.set_title(f"{regime}: {len(bases)} preselected timing bases")
        axis.set_ylim(-.02, 1.04)
        axis.set_xscale("symlog", linthresh=1e-6)
        axis.set_xlim(0, max(horizon, 1e-9) * 1.05)
        axis.set_xlabel("Run-median solve + verification time (seconds)")
        axis.set_ylabel("Observed eligible completion fraction")
        axis.grid(alpha=.15)
        if not bases:
            _empty(axis, "No predeclared timing cohort")
    if handles:
        fig.legend(list(handles.values()), list(handles), loc="lower center", ncol=4, fontsize=7, frameon=False)
    fig.suptitle("Observed completion fractions in the preselected timing cohort\nFailures retain their denominator weight; not Kaplan–Meier or first-hit timing", y=.99)
    fig.tight_layout(rect=(0, min(.3, .055 + len(handles)*.006), 1, .93))
    _save(fig, directory, "observed-completion")

    qualified = [row for row in bundle["references"] if row["quality"] in {"ANALYTIC", "VERIFIED_ENCLOSURE"}]
    fig, axis = plt.subplots(figsize=(8, 6))
    for row in qualified:
        low, high, certificate = (Fraction(row[key]) for key in ["lower", "upper", "certificate"])
        color = "#bc3030" if low > certificate else "#236784"
        axis.vlines(float(certificate), float(low), float(high), color=color, alpha=.28, linewidth=.7)
        axis.scatter([float(certificate)]*2, [float(low), float(high)], marker="_", s=12, color=color, alpha=.45)
    axis.plot([0, 1], [0, 1], color="#555555", lw=.9, ls="--", label="Error = certificate")
    axis.set(xlim=(-.02, 1.02), ylim=(-.02, 1.02), xlabel="Certified upper bound", ylabel="Qualified true-error bracket")
    axis.set_title(f"Scalar qualified reference brackets ({len(qualified)} stored time rows)\nRed only if the exact lower endpoint exceeds the certificate")
    axis.legend(frameon=False)
    if not qualified:
        _empty(axis, "No qualified scalar reference brackets available\nNo joint two-dimensional error claim")
    fig.text(.5, .015, "Bracket endpoints are bounds, not point estimates; repeated times are not independent instances.", ha="center", fontsize=8)
    fig.tight_layout(rect=(0, .04, 1, 1))
    _save(fig, directory, "qualified-reference-certificate")

    fig, axes = plt.subplots(1, 3, figsize=(14, 5.5))
    endpoints = [("certificationDifference", "Certification difference\nLearned minus deterministic", 0),
                 ("conditionalTimeRatio", "Conditional paired time ratio\nPreselected timing cohort; both certified", 1),
                 ("conditionalMapImageRatio", "Conditional paired work ratio\nAll bases; both certified", 1)]
    for axis, (endpoint, title, reference) in zip(axes, endpoints):
        selected = [row for row in result["effects"] if row["endpoint"] == endpoint]
        axis.axvline(reference, color="#777777", linestyle="--", linewidth=.8)
        for y, effect in enumerate(selected):
            if effect["estimate"] is None:
                axis.text(.5, y, "unavailable", transform=axis.get_yaxis_transform(), ha="center", fontsize=8)
                continue
            estimate = effect["estimate"]
            axis.scatter([estimate], [y], color="#236784", zorder=3)
            if effect["ci95"] is not None:
                low, high = effect["ci95"]
                axis.hlines(y, low, high, color="#236784", linewidth=1.4)
            else:
                axis.annotate("CI unavailable", (estimate, y), xytext=(4, 4), textcoords="offset points", fontsize=7)
        axis.set_yticks(range(len(selected)), [row["scope"] for row in selected])
        axis.set_ylim(len(selected) - .5, -.5)
        axis.set_title(title)
        axis.grid(axis="x", alpha=.15)
        if endpoint != "certificationDifference":
            axis.set_xscale("log")
        else:
            axis.set_xlim(-1.05, 1.05)
    fig.suptitle("Paired grouped bootstrap effects with 95% percentile intervals", y=.99)
    fig.text(.5, .025, "Only aggregate certification difference is primary. Conditional ratios are not unconditional speedups.\nWhole bases and globally shared model seeds are resampled; per-regime results are descriptive.", ha="center", fontsize=9)
    fig.tight_layout(rect=(0, .12, 1, .93))
    _save(fig, directory, "bootstrap-effects")
