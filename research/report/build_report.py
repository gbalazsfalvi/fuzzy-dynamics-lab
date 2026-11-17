"""Format saved final evidence as TeX; never run a solver, model or analysis.

Generated TeX and alternate paper layouts use saved summaries without statistical
recomputation. Optional Tectonic compilation writes the actual PDF to --output.
Missing final inputs fail closed; no empirical placeholder data are created.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import subprocess

try:
    from .paper_figures import render as render_paper_figures
except ImportError:
    from paper_figures import render as render_paper_figures

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
REGIMES = ("id-1d", "id-2d", "ood-1d", "ood-2d")
FIGURES = {
    "outcome-heatmap": "Certification outcomes for every declared method and regime. Unavailable methods remain explicit; individual seeds are retained.",
    "paired-algorithm-work": "Paired CAE map-image work, including failed runs. This compares the shared engine's counters, not the costs of exact rational reference representations.",
    "observed-completion": "Observed completion fractions on the preselected timing bases. Eligible failures remain in the denominator; these are run medians, not first-hit or censoring-adjusted curves.",
    "qualified-reference-certificate": "Qualified scalar reference brackets versus numerical certificates. Upper-only exceedances are inconclusive; scalar evidence supplies no joint two-dimensional validation.",
    "bootstrap-effects": "Frozen paired effects and grouped percentile intervals. Certification difference is primary; time/work ratios are conditional descriptive comparisons.",
}


def digest(path: Path) -> str:
    """Hash stored bytes incrementally, including compressed evidence bytes."""
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def tex(value: object) -> str:
    """Escape data as literal TeX text, including method IDs and descriptions."""
    escapes = {"\\": r"\textbackslash{}", "&": r"\&", "%": r"\%", "$": r"\$",
               "#": r"\#", "_": r"\_", "{": r"\{", "}": r"\}",
               "~": r"\textasciitilde{}", "^": r"\textasciicircum{}"}
    return "".join(escapes.get(char, char) for char in str(value))


def number(value: object) -> str:
    """Keep missing conditional estimates absent; reject nonfinite values."""
    if value is None:
        return "unavailable"
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("Expected a finite numerical summary or unavailable")
    return f"{value:.5g}"


def interval(value: object) -> str:
    if value is None:
        return "unavailable"
    if not isinstance(value, list) or len(value) != 2 or value[0] > value[1]:
        raise ValueError("Malformed percentile interval")
    return "[" + number(value[0]) + ", " + number(value[1]) + "]"


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"),
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"Nonfinite JSON: {value}")))


def safe_evidence(directory: Path, relative: str) -> Path:
    """Resolve one reviewed relative artifact path without accepting escapes."""
    if not re.fullmatch(r"[A-Za-z0-9_./-]+", relative) or ".." in Path(relative).parts:
        raise ValueError("Unsafe evidence path")
    target = (directory / relative).resolve(strict=True)
    if not target.is_relative_to(directory.resolve()) or not target.is_file():
        raise ValueError("Evidence path escapes its input directory")
    return target


def verify_payloads(directory: Path, runs: list[dict]) -> None:
    """Hash eligible payloads while preserving explicit unavailable outcomes."""
    for run in runs:
        relative, expected = run.get("evidenceFile"), run.get("evidenceSha256")
        if run["status"] == "UNAVAILABLE":
            if relative is not None or expected is not None or run.get("timings") != []:
                raise ValueError("Unavailable row cannot carry payload evidence or timings")
            continue
        if not isinstance(relative, str) or not isinstance(expected, str):
            raise ValueError("Available row must carry payload evidence and its digest")
        if digest(safe_evidence(directory, relative)) != expected:
            raise ValueError("Saved numerical payload hash mismatch")


def require_verified_replay(manifest_path: Path, freeze_path: Path) -> None:
    """Share the unfrozen exact-protocol/actual-Java-receipt publication gate."""
    source = HERE.parents[1] / "tools/analyze_verified.py"
    spec = importlib.util.spec_from_file_location("fuzzy_verified_analysis_preflight", source)
    if spec is None or spec.loader is None:
        raise ValueError("Verified-analysis publication preflight is unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.validate(root=ROOT, manifest=manifest_path, freeze=freeze_path)


def validate(analysis_path: Path, manifest_path: Path, freeze_path: Path) -> tuple[dict, dict]:
    """Require the final frozen cohort, matching provenance and stored hashes.

    Numerical geometry is not re-proved here. The Java saved-artifact replay and
    the analysis loader remain separate prerequisites, documented in the study.
    """
    analysis, manifest, freeze = read(analysis_path), read(manifest_path), read(freeze_path)
    if analysis.get("analysisVersion") != "analysis-v1" or manifest.get("schemaVersion") != "comparison-evidence-v1":
        raise ValueError("Unsupported saved analysis/evidence schema")
    protocol = manifest["protocol"]
    if protocol.get("stage") != "FROZEN_HELD_OUT" or re.search(r"fixture|synthetic", protocol["id"], re.I):
        raise ValueError("Final report requires actual frozen held-out evidence, not a layout fixture")
    require_verified_replay(manifest_path, freeze_path)
    if analysis["protocolId"] != protocol["id"]:
        raise ValueError("Analysis protocol differs from manifest")
    for analysis_key, source_key in [("frozenSourceSha256", "sourceSha256"),
                                     ("frozenProtocolSha256", "protocolSha256")]:
        if analysis[analysis_key] != manifest[source_key] or manifest[source_key] != freeze[source_key]:
            raise ValueError(f"Frozen provenance mismatch: {source_key}")
    if digest(freeze_path) != analysis["freezeSha256"]:
        raise ValueError("Freeze bytes differ from analyzed freeze")
    if analysis["frozenSelection"] != freeze:
        raise ValueError("Frozen selection differs from analyzed selection")
    for key in ("primaryDeterministicMethod", "primaryLearnedFamily", "primaryLearningMethodIds"):
        if analysis[key] != freeze[key]:
            raise ValueError("Primary comparison differs from frozen selection")
    if analysis["inputHashes"].get("manifest.json") != digest(manifest_path):
        raise ValueError("Manifest bytes differ from analyzed evidence")
    for relative, expected in analysis["inputHashes"].items():
        if digest(safe_evidence(manifest_path.parent, relative)) != expected:
            raise ValueError(f"Analyzed input hash mismatch: {relative}")
    verify_payloads(manifest_path.parent, manifest["runs"])
    if analysis["referenceAudit"]["violations"]:
        raise ValueError("Qualified lower-bound violation blocks a publication PDF")
    if set(analysis["designCoverage"]) != set(REGIMES):
        raise ValueError("Missing declared test regime")
    if any(analysis["designCoverage"][regime] != {"baseProblems": 30, "timingBases": 3} for regime in REGIMES):
        raise ValueError("Report narrative requires the frozen 120-base/12-timing-base design")
    expected = {"uniform", "maximum-bound", "next-step", "predicted-gain", "ridge",
                *(f"mlp-seed-{seed}" for seed in (11, 23, 37, 51, 71)),
                *(f"random-seed-{seed}" for seed in range(20))}
    methods = analysis["declaredMethods"]
    if len(methods) != 32 or len(set(methods)) != 32 or not expected.issubset(methods):
        raise ValueError("Missing frozen method/seed cohort")
    if methods != [method["id"] for method in protocol["methods"]]:
        raise ValueError("Analysis method list differs from embedded protocol")
    problems = {problem["id"]: problem for problem in protocol["problems"]}
    actual_counts = Counter()
    for run in manifest["runs"]:
        problem = problems[run["problemId"]]
        regime = next((regime for regime in REGIMES if problem["id"].startswith("test-" + regime + "-")), None)
        if regime is None:
            raise ValueError("Unexpected held-out problem regime")
        actual_counts[run["methodId"], regime, run["status"]] += 1
    cells = {(row["method"], row["regime"]) for row in analysis["methodSummary"]}
    if len(analysis["methodSummary"]) != 128 or cells != {(method, regime) for method in methods for regime in REGIMES}:
        raise ValueError("Incomplete method-by-regime summary")
    for row in analysis["methodSummary"]:
        if row["requested"] != sum(row[key] for key in ("certified", "budgetExhausted", "otherFailures", "unavailable")):
            raise ValueError("A failure or unavailable result was omitted")
        statuses = {status: count for (method, regime, status), count in actual_counts.items()
                    if method == row["method"] and regime == row["regime"]}
        if row["statuses"] != dict(sorted(statuses.items())) or sum(statuses.values()) != row["requested"]:
            raise ValueError("Summary status counts differ from saved manifest")
    bootstrap = analysis["bootstrap"]
    if bootstrap["replicates"] != 10000 or bootstrap["seed"] != 20261117:
        raise ValueError("Final analysis did not use the declared bootstrap")
    for name in FIGURES:
        figure = analysis_path.parent / "figures" / f"{name}.pdf"
        if not figure.is_file() or not figure.read_bytes().startswith(b"%PDF-"):
            raise ValueError(f"Missing actual analysis figure: {name}")
    return analysis, manifest


def evidence_tex(analysis: dict, manifest: dict, analysis_path: Path, manifest_path: Path,
                 paper_directory: Path | None = None) -> str:
    """Render public fingerprints, not local machine paths or SSH metadata."""
    facts = [("Analysis JSON", digest(analysis_path)), ("Evaluation manifest", digest(manifest_path)),
             ("Frozen protocol", analysis["frozenProtocolSha256"]),
             ("Frozen source/build inputs", analysis["frozenSourceSha256"]),
             ("Freeze JSON", analysis["freezeSha256"]),
             ("Analysis plan", analysis["analysisPlanSha256"])]
    if paper_directory is not None:
        facts += [("Paper-layout provenance", digest(paper_directory / "paper-layout-provenance.json")),
                  ("Paper renderer source", digest(HERE / "paper_figures.py"))]
    lines = [r"\newcommand{\StudyEvidence}{", "The following SHA-256 values identify the exact saved inputs used by this report.",
             r"\begin{description}[style=nextline]"]
    for label, value in facts:
        if not re.fullmatch(r"[0-9a-f]{64}", value):
            raise ValueError("Invalid artifact fingerprint")
        lines.append(r"\item[" + tex(label) + r"] {\footnotesize\texttt{" + value[:32] + r"\allowbreak " + value[32:] + "}}")
    lines += [r"\end{description}", "Protocol: " + tex(analysis["protocolId"]) + r".\\",
              "Producer timestamp (not a timing result): " + tex(manifest["generatedAtUtc"]) + ".",
              "Analysis runtime: " + tex(json.dumps(analysis["runtime"], sort_keys=True)) + ".",
              "A matching hash is a consistency check, not authentication. Numerical replay is required separately.", "}"]
    return "\n".join(lines) + "\n"


def results_tex(analysis: dict, manifest: dict, analysis_path: Path,
                paper_directory: Path | None = None) -> str:
    """Produce compact complete outcomes, paired estimates and actual plots."""
    primary = next(row for row in analysis["effects"] if row["endpoint"] == "certificationDifference" and row["scope"] == "all")
    lines = ["The frozen primary comparison is " + tex(analysis["primaryLearnedFamily"]) + " (" +
             str(len(analysis["primaryLearningMethodIds"])) + " retained model(s)) versus " + tex(analysis["primaryDeterministicMethod"]) + ".",
             "The equal-regime certification-rate difference is \\textbf{" + number(primary["estimate"]) + "}, with 95\\% grouped percentile interval " + interval(primary["ci95"]) + ". Positive favors the learned family.",
             "There are 120 held-out base problems; 12 were preselected for timing. Repeated times and model seeds do not increase the base-problem count.",
             r"\subsection{All methods and failure outcomes}",
             "Table entries retain every declared seed. R is requested rows; C certified; B budget exits; F other failures; U unavailable. Each regime column is certified/requested. These raw counts do not replace equal-base weighting in the primary estimator.",
             r"{\small\setlength{\tabcolsep}{3pt}",
             r"\begin{longtable}{@{}p{3.3cm}rrrrrrrrr@{}}",
             r"\toprule Method & R & C & B & F & U & ID 1D & ID 2D & OOD 1D & OOD 2D\\\midrule\endhead"]
    indexed = {(row["method"], row["regime"]): row for row in analysis["methodSummary"]}
    for method in analysis["declaredMethods"]:
        rows = [indexed[method, regime] for regime in REGIMES]
        totals = [sum(row[key] for row in rows) for key in ("requested", "certified", "budgetExhausted", "otherFailures", "unavailable")]
        lines.append(tex(method) + " & " + " & ".join(map(str, totals)) + " & " +
                     " & ".join(f"{row['certified']}/{row['requested']}" for row in rows) + r"\\")
    lines += [r"\bottomrule\end{longtable}}", r"\clearpage", r"\subsection{Paired effects}",
              "Ratios are learned/deterministic, restricted to both-certified pairs with positive costs; time ratios use only preselected timing bases. C/pairs means jointly certified / declared pairs. The primary certification difference uses every declared pair. All nonprimary intervals are descriptive.",
              r"{\footnotesize\setlength{\tabcolsep}{3pt}\begin{longtable}{@{}>{\raggedright\arraybackslash}p{3.7cm}lr>{\raggedright\arraybackslash}p{2.8cm}rrr@{}}",
              r"\toprule Endpoint & Scope & Estimate & 95\% interval & Bases & C/pairs & Valid draws\\\midrule\endhead"]
    labels = {"certificationDifference": "Certification difference", "conditionalTimeRatio": "Conditional time ratio", "conditionalMapImageRatio": "Conditional work ratio"}
    for row in analysis["effects"]:
        lines.append(" & ".join([tex(labels[row["endpoint"]]), tex(row["scope"]), number(row["estimate"]), interval(row["ci95"]),
                                 str(row["baseProblems"]), f"{row['bothCertifiedPairs']}/{row['declaredPairs']}",
                                 f"{row['validBootstrapReplicates']}/{row['bootstrapReplicates']}"]) + r"\\")
    lines += [r"\bottomrule\end{longtable}}", r"\subsection{Qualified checks and missing-cut diagnostics}"]
    audit = analysis["referenceAudit"]
    lines.append(f"Qualified scalar reference rows: {audit['qualifiedRows']}; verified lower-endpoint violations: {len(audit['violations'])}; upper-only exceedances (inconclusive): {audit['upperOnlyExceedances']}; unqualified rows: {audit['unqualifiedRows']}.")
    lines.append("No violation in these saved qualified cases is a finite observation, not a continuous proof or joint two-dimensional validation.")
    lines += [r"\begin{center}\begin{tabular}{lr}\toprule Alpha-cut status & Stored rows\\\midrule"]
    for status, count in sorted(analysis["alphaStatusCounts"].items()):
        lines.append(tex(status) + f" & {count}" + r"\\")
    lines += [r"\bottomrule\end{tabular}\end{center}", "Empty-cut distances remain absent; they are not replaced with zero."]
    cost = analysis["trainingBreakEven"]
    if cost.get("repeatedSolves") is None:
        lines.append("Training-cost break-even is unavailable: " + tex(cost["reason"]) + ".")
    else:
        lines.append("Under the supplied complete comparable cost scenario only, break-even is " + number(cost["repeatedSolves"]) + " repeated solves, with mean paired savings " + number(cost["meanSavingsSeconds"]) + " seconds. This is not a deployment forecast.")
    lines += [r"\subsection{Readable paper layouts}",
              "These alternative layouts display the existing saved summaries and effects. They do not recompute estimators, resample, select methods or alter the original analysis figures. Every method, seed, regime, status and effect scope remains represented."]
    paper_directory = paper_directory or ROOT / "output/pdf/figures"
    paper_captions = {
        "paper-outcomes-1": "Complete declared outcomes, methods 1--16 in their original declared order. C is certified/requested, B budget exits, N numerical failures, X cancellations, and U unavailable. Colour is the saved equal-base certification fraction; grey means unavailable.",
        "paper-outcomes-2": "Complete declared outcomes, methods 17--32 in the same declared order, including every remaining random seed and the restricted scalar baselines. Status counts and unavailable rows are retained.",
        "paper-effects": "All three saved paired endpoints, stacked at paper scale. Every aggregate/regime estimate, interval and unavailable state is retained. Only aggregate certification is primary; conditional time/work ratios and per-regime comparisons are descriptive.",
    }
    for name, caption in paper_captions.items():
        figure = paper_directory / f"{name}.pdf"
        relative = os.path.relpath(figure, HERE).replace("\\", "/")
        if any(char in relative for char in "{}%#"):
            raise ValueError("Unsupported TeX figure path")
        height = "0.85" if name == "paper-effects" else "0.76"
        lines += [r"\begin{figure}[p]\centering",
                  r"\includegraphics[width=\linewidth,height=" + height + r"\textheight,keepaspectratio]{\detokenize{" + relative + "}}",
                  r"\caption{" + tex(caption) + r"}\end{figure}"]
    lines.append(r"\clearpage")
    lines += [r"\subsection{Original downloadable analysis vectors}",
              "The complete data archive retains every original vector PDF and its PNG/SVG counterparts unchanged. Dense work, completion and reference plots are downloadable at their native size, rather than reduced to unreadable paper thumbnails.",
              r"\begin{itemize}"]
    archive_url = "https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/releases/download/v0.1.0/fuzzy-dynamics-lab-v0.1.0-data.zip"
    for name, description in FIGURES.items():
        lines.append(r"\item\href{" + archive_url + "}{" + tex(name + ".pdf") + "}: " + tex(description))
    lines += [r"\end{itemize}", "The paper-layout provenance JSON records the analysis, all 15 original figure files and renderer source hashes. Original statistical outputs remain authoritative."]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--analysis", type=Path, default=ROOT / "artifacts/evaluation-analysis/analysis.json")
    parser.add_argument("--manifest", type=Path, default=ROOT / "artifacts/evaluation/manifest.json")
    parser.add_argument("--freeze", type=Path, default=ROOT / "research/evaluation-freeze-v1.json")
    parser.add_argument("--compiler", type=Path, help="Explicit Tectonic executable; omission prepares TeX only")
    parser.add_argument("--output", type=Path, default=ROOT / "output/pdf")
    args = parser.parse_args()
    analysis, manifest = validate(args.analysis.resolve(), args.manifest.resolve(), args.freeze.resolve())
    output = args.output.resolve()
    if output == args.manifest.parent.resolve() or output.is_relative_to(args.manifest.parent.resolve()):
        raise ValueError("PDF output cannot overlap numerical evidence")
    paper_directory = output / "figures"
    render_paper_figures(args.analysis.resolve(), paper_directory)
    # Complete all checks and formatting before replacing either include.
    results = results_tex(analysis, manifest, args.analysis.resolve(), paper_directory)
    evidence = evidence_tex(analysis, manifest, args.analysis.resolve(), args.manifest.resolve(), paper_directory)
    generated = HERE / "generated"
    generated.mkdir(exist_ok=True)
    (generated / "results.tex").write_text(results, encoding="utf-8")
    (generated / "evidence.tex").write_text(evidence, encoding="utf-8")
    if args.compiler:
        compiler = args.compiler.resolve(strict=True)
        output.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(compiler), "--keep-logs", "--outdir", str(output), "main.tex"], cwd=HERE, check=True)
        pdf = output / "main.pdf"
        if not pdf.is_file() or not pdf.read_bytes().startswith(b"%PDF-"):
            raise ValueError("Compiler did not export a PDF")
        print(f"Exported PDF: {pdf}; SHA-256: {digest(pdf)}")
    else:
        print("Prepared verified results.tex and evidence.tex; PDF not compiled.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
