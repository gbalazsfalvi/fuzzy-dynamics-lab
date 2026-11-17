# Frozen saved-artifact analysis

The estimands and timing cohort were specified in [ANALYSIS_PLAN.md](../docs/ANALYSIS_PLAN.md) before final outcomes. This code reads saved evidence; it never imports learning modules or reruns a solver.

From the repository root on Windows:

```powershell
.\learning\.venv\Scripts\python.exe -m unittest discover -s analysis -p 'test_*.py' -v
.\learning\.venv\Scripts\python.exe tools/replay_evaluation.py
.\learning\.venv\Scripts\python.exe tools/analyze_verified.py
```

The publication wrapper requires a matching actual Java replay receipt, checks the raw locked protocol file against the freeze and compares its exact embedded grouping/timing/cohort, then calls the unchanged frozen analyzer. It adds no estimator or model selection. Use `--verify-only` for admission alone. Direct `analysis/evaluate.py` is available for an already verified bundle; its copied source/protocol labels rely on that separate typed Java replay rather than independently authenticating the embedded protocol.

The underlying analyzer defaults to `artifacts/evaluation`, `research/evaluation-freeze-v1.json`, and `artifacts/evaluation-analysis`. Override these with `--input`, `--freeze`, and `--output` on that direct CLI. `--training-cost` optionally supplies an explicit complete/comparable cost record; otherwise amortization remains unavailable. The declared 10,000 bootstrap draws and seed 20261117 are fixed in the CLI.

Run the project's Java saved-evidence verification separately before reporting results. The Python reader checks complete paired rows, CSV/manifest agreement, exact strings, timing-cohort membership, payload hashes, and any `analysisSha256` mapping in the freeze. It does not independently establish the mathematical truth of stored numerical geometry. Hashes provide consistency, not authentication.

Numerical payloads may be `runs/<safe-id>.json` or `runs/<safe-id>.json.gz`. Their `evidenceSha256` hashes the actual stored bytes, including gzip compression. After checking that hash, the reader decodes one payload at a time with a 64 MiB decoded-size limit and validates that it is a JSON object. It does not retain the full cohort's geometry in memory. The statistical estimators are identical for both storage formats.

Outputs include JSON/Markdown analysis, complete method/regime summaries, paired effects, per-run timing summaries, and five standalone figures in PNG, SVG and PDF. A qualified lower error endpoint above its certificate writes diagnostic outputs and returns exit code 2. Malformed evidence returns exit code 1. Successful analysis without a verified violation returns 0. Upper-only exceedances are inconclusive, not failures.

`test_artifacts.py` builds explicitly synthetic data in temporary directories. The tests do not read final evaluation artifacts. `.qa/` is ignored local synthetic visual QA, never experimental results. All raw model seeds and failure outcomes remain in the real input artifacts and complete tables. Only the 12 preselected timing bases enter runtime comparisons; all 120 bases enter certification and algorithm-work analysis.
