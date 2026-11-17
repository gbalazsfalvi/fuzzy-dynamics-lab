"""Readable paper layouts of saved summaries; no statistical recomputation.

No resampling, fitting, model selection, raw outcome analysis or solver calls.
Original analysis figures remain unchanged. All display inputs and original
figure hashes are recorded with the renderer source hash as layout provenance.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
from pathlib import Path

REGIMES = ("id-1d", "id-2d", "ood-1d", "ood-2d")
SCOPES = ("all", *REGIMES)
ENDPOINTS = ("certificationDifference", "conditionalTimeRatio", "conditionalMapImageRatio")
ORIGINALS = ("outcome-heatmap", "paired-algorithm-work", "observed-completion",
             "qualified-reference-certificate", "bootstrap-effects")
PAPER_NAMES = ("paper-outcomes-1", "paper-outcomes-2", "paper-effects")


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def finite(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value)


def layout_map(analysis: dict) -> dict:
    """Map every original method/cell/effect to a fixed display slot verbatim."""
    methods = analysis["declaredMethods"]
    if len(methods) != 32 or len(set(methods)) != 32:
        raise ValueError("Paper layout requires all 32 unique declared methods")
    rows = analysis["methodSummary"]
    indexed = {(row["method"], row["regime"]): row for row in rows}
    if len(rows) != 128 or set(indexed) != {(method, regime) for method in methods for regime in REGIMES}:
        raise ValueError("Paper outcome layout must retain every method/regime cell")
    panels = []
    for start in (0, 16):
        selected = methods[start:start + 16]
        cells = []
        for method in selected:
            for regime in REGIMES:
                row = indexed[method, regime]
                value = row["baseWeightedSuccessFraction"]
                if not finite(value) or not 0 <= value <= 1:
                    raise ValueError("Invalid saved certification fraction")
                if sum(row["statuses"].values()) != row["requested"]:
                    raise ValueError("Saved status counts do not cover requested outcomes")
                cells.append({"method": method, "regime": regime, "value": value,
                              "requested": row["requested"], "eligible": row["eligible"],
                              "statuses": copy.deepcopy(row["statuses"])})
        panels.append({"methods": list(selected), "cells": cells})
    effects = analysis["effects"]
    indexed_effects = {(row["endpoint"], row["scope"]): row for row in effects}
    if len(effects) != 15 or set(indexed_effects) != {(endpoint, scope) for endpoint in ENDPOINTS for scope in SCOPES}:
        raise ValueError("Paper effects must retain all three endpoints and every scope")
    effect_panels = []
    for endpoint in ENDPOINTS:
        display = []
        for scope in SCOPES:
            row = indexed_effects[endpoint, scope]
            if row["estimate"] is not None and not finite(row["estimate"]):
                raise ValueError("Invalid saved effect estimate")
            ci = row["ci95"]
            if ci is not None and (len(ci) != 2 or not all(finite(value) for value in ci) or ci[0] > ci[1]):
                raise ValueError("Invalid saved percentile interval")
            display.append(copy.deepcopy(row))
        effect_panels.append({"endpoint": endpoint, "rows": display})
    return {"outcomePanels": panels, "effectPanels": effect_panels}


def _save(figure, output: Path, name: str) -> None:
    for extension in ("pdf", "png"):
        metadata = ({"Creator": "Fuzzy Dynamics Lab paper layout; saved summaries only",
                     "CreationDate": None, "ModDate": None} if extension == "pdf" else
                    {"Software": "Fuzzy Dynamics Lab paper layout; saved summaries only"})
        # Fixed canvas preserves the intended physical 9-point label size.
        figure.savefig(output / f"{name}.{extension}", dpi=200, metadata=metadata)


def render(analysis_path: Path, output: Path, *, synthetic: bool = False) -> dict:
    """Render only saved display values; write a complete layout provenance map."""
    analysis_path, output = analysis_path.resolve(), output.resolve()
    if output == analysis_path.parent or output.is_relative_to(analysis_path.parent):
        raise ValueError("Paper layout output must be separate from original analysis")
    analysis = json.loads(analysis_path.read_text(encoding="utf-8"),
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Nonfinite analysis JSON")))
    if analysis.get("analysisVersion") != "analysis-v1":
        raise ValueError("Unsupported saved analysis version")
    if not synthetic and ("synthetic" in analysis["protocolId"].lower() or "fixture" in analysis["protocolId"].lower()):
        raise ValueError("Synthetic inputs must carry the explicit synthetic layout label")
    before = digest(analysis_path)
    renderer_before = digest(Path(__file__))
    layout = layout_map(analysis)
    originals = {}
    for name in ORIGINALS:
        for extension in ("png", "svg", "pdf"):
            path = analysis_path.parent / "figures" / f"{name}.{extension}"
            if path.is_file():
                originals[f"{name}.{extension}"] = digest(path)
            elif not synthetic:
                raise ValueError("Original analysis figure missing: " + path.name)
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    matplotlib.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9,
                               "axes.titlesize": 10, "axes.labelsize": 9,
                               "xtick.labelsize": 9, "ytick.labelsize": 9,
                               "pdf.fonttype": 42, "svg.fonttype": "none"})
    output.mkdir(parents=True, exist_ok=True)
    banner = "SYNTHETIC LAYOUT FIXTURE - NOT EXPERIMENTAL RESULTS" if synthetic else "Saved analysis values; paper layout only"
    color = plt.get_cmap("viridis").copy()
    color.set_bad("#ededed")
    for index, panel in enumerate(layout["outcomePanels"], 1):
        fig, axis = plt.subplots(figsize=(6.3, 6.5))
        fig.subplots_adjust(left=.245, right=.985, top=.90, bottom=.18)
        cells = {(cell["method"], cell["regime"]): cell for cell in panel["cells"]}
        matrix = [[cells[method, regime]["value"] if cells[method, regime]["eligible"] else float("nan")
                   for regime in REGIMES] for method in panel["methods"]]
        image = axis.imshow(matrix, vmin=0, vmax=1, cmap=color, aspect="auto")
        axis.set_xticks(range(4), REGIMES)
        axis.set_yticks(range(16), panel["methods"])
        axis.tick_params(length=0, pad=5)
        for y, method in enumerate(panel["methods"]):
            for x, regime in enumerate(REGIMES):
                cell = cells[method, regime]
                counts = cell["statuses"]
                certified = counts.get("CERTIFIED", 0)
                label = (f"C{certified}/{cell['requested']} B{counts.get('BUDGET_EXHAUSTED', 0)}\n"
                         f"N{counts.get('NUMERICAL_FAILURE', 0)} X{counts.get('CANCELLED', 0)} U{counts.get('UNAVAILABLE', 0)}")
                axis.text(x, y, label, ha="center", va="center", fontsize=9,
                          color="white" if cell["eligible"] and cell["value"] < .55 else "#161616")
        fig.suptitle(f"All declared outcomes: methods {(index-1)*16+1}-{index*16} of 32", y=.977, fontsize=11, fontweight="bold")
        fig.text(.5, .933, banner, ha="center", fontsize=8.5, color="#a32525" if synthetic else "#444444")
        bar_axis = fig.add_axes([.30, .10, .62, .018])
        bar = fig.colorbar(image, cax=bar_axis, orientation="horizontal", ticks=[0, .25, .5, .75, 1])
        bar.set_label("Saved equal-base certification fraction; grey = unavailable", fontsize=9, labelpad=2)
        fig.text(.5, .014, "C certified / requested; B budget; N numerical failure; X cancelled; U unavailable", ha="center", fontsize=8.5)
        _save(fig, output, f"paper-outcomes-{index}")
        plt.close(fig)
    fig, axes = plt.subplots(3, 1, figsize=(6.3, 8.0))
    fig.subplots_adjust(left=.19, right=.97, top=.905, bottom=.115, hspace=.70)
    titles = ("Certification difference (primary aggregate endpoint)",
              "Conditional time ratio (preselected timing cohort)",
              "Conditional map-image ratio (all paired bases)")
    for axis, panel, title in zip(axes, layout["effectPanels"], titles):
        endpoint = panel["endpoint"]
        reference = 0 if endpoint == "certificationDifference" else 1
        axis.axvline(reference, color="#777777", ls="--", lw=.8)
        for y, row in enumerate(panel["rows"]):
            if row["estimate"] is None:
                axis.text(.5, y, "unavailable", transform=axis.get_yaxis_transform(), ha="center", va="center", fontsize=9)
                continue
            if row["ci95"] is not None:
                lower, upper = row["ci95"]
                axis.plot([lower, upper], [y, y], lw=1.3, color="#276582")
                axis.plot([lower, upper], [y, y], "|", ms=7, color="#276582")
            else:
                axis.annotate("CI unavailable", (row["estimate"], y), xytext=(5, 5), textcoords="offset points", fontsize=8)
            axis.plot(row["estimate"], y, "o", ms=4, color="#123f55")
        axis.set_yticks(range(5), [row["scope"] for row in panel["rows"]])
        axis.set_ylim(4.6, -.6)
        axis.set_title(title, loc="left", fontsize=10, fontweight="bold", pad=8)
        axis.set_xlabel("Learned minus deterministic" if reference == 0 else "Learned / deterministic; both certified, positive cost", fontsize=9)
        axis.grid(axis="x", alpha=.18)
        axis.spines[["top", "right"]].set_visible(False)
    fig.suptitle("Saved paired estimates and 95% grouped intervals", y=.98, fontsize=11, fontweight="bold")
    fig.text(.5, .945, banner, ha="center", fontsize=8.5, color="#a32525" if synthetic else "#444444")
    fig.text(.5, .014, "All scopes retained; only aggregate certification is primary.\nConditional ratios and per-regime comparisons are descriptive.", ha="center", fontsize=8.3)
    _save(fig, output, "paper-effects")
    plt.close(fig)
    if digest(analysis_path) != before:
        raise ValueError("Saved analysis changed during paper rendering")
    if digest(Path(__file__)) != renderer_before:
        raise ValueError("Paper renderer source changed during rendering")
    for name, expected in originals.items():
        if digest(analysis_path.parent / "figures" / name) != expected:
            raise ValueError("Original figure changed during paper rendering")
    provenance = {"schemaVersion": "paper-layout-v1", "syntheticFixture": synthetic,
                  "scope": "DISPLAY_ONLY_NO_STATISTICAL_RECOMPUTATION",
                  "sourceAnalysisSha256": before, "rendererSha256": renderer_before,
                  "originalFigureSha256": originals, "matplotlibVersion": matplotlib.__version__,
                  "labelPoints": 9, "canvasWidthInches": 6.3, "layoutMap": layout,
                  "outputSha256": {f"{name}.{extension}": digest(output / f"{name}.{extension}")
                                   for name in PAPER_NAMES for extension in ("pdf", "png")}}
    (output / "paper-layout-provenance.json").write_text(json.dumps(provenance, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8")
    return provenance


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--analysis", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--synthetic-fixture", action="store_true")
    args = parser.parse_args()
    render(args.analysis, args.output, synthetic=args.synthetic_fixture)
