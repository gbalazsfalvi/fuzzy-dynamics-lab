# Research study source and PDF export

`main.tex` is the English mathematical/software study for release 0.1.0.
Its final results come from the verified saved analysis, including the negative
primary learned-policy result. The source gives
complete assumptions, P1's two directed proofs, P2, conditional P3, precision and
budget limits, the nonmonotone counterexample, exact scalar reference geometry,
published PWL attribution, frozen learning/evaluation design, and review limits.

The project was created with the Codex LaTeX project tool. The built-in compiler
can preview/diagnose it, but its compile API does not export a downloadable PDF.
Use Tectonic for the actual final file. No global TeX installation is required.

## Final evidence import

From the repository root, using the existing locked Python environment:

```powershell
.\learning\.venv\Scripts\python.exe research/report/build_report.py --compiler .local/tools/tectonic-0.17.0/bin/tectonic.exe
```

The defaults read `artifacts/evaluation-analysis/analysis.json`,
`artifacts/evaluation/manifest.json`, and `research/evaluation-freeze-v1.json`.
CLI options can specify those three paths and `--output`. Without `--compiler`,
`generated/results.tex`, `generated/evidence.tex` and alternate paper figures
under `output/pdf/figures/` are written. With it, Tectonic also writes an actual
`output/pdf/main.pdf` and compiler log. A first compiler
run downloads the standard TeX bundle into its local tool cache; allow network
access or prewarm that cache before an offline build.

The formatter does not import learning/analysis modules, run Java, train, select,
rerun an evaluation, alter evidence, or recompute statistical estimates. It hashes stored
input/payload bytes, requires matching source/protocol/freeze, the frozen
120-base/12-timing-base design, 32 declared methods, complete per-regime status
summaries, the declared 10,000-draw bootstrap and all five saved PDF figures.
Any qualified reference lower-bound violation blocks a publication PDF. It
escapes textual data and preserves unavailable statistics as unavailable.
These checks are not a numerical proof checker: complete Java saved-artifact
replay and the validated analysis run are independent prerequisites.

No final file is synthesized if inputs are missing. Do not create fake includes
to force a final compile. `paper_figures.py` maps the saved summaries into two
readable 16-method outcome panels and stacks all three saved effect endpoints.
Every method, regime, status count, estimate, interval and unavailable state is
retained; no estimator, bootstrap or selection is rerun. The 6.3-inch canvases
use 9-point labels at the report's A4 text width. Outcome/effect tables use
9/8-point text with a wrapping confidence-interval column.

All 15 original analysis figure files remain unchanged and downloadable. Dense
work/completion/reference plots are linked by readable filename to the full data
ZIP instead of embedded as tiny thumbnails. `paper-layout-provenance.json`
records the source analysis, every original figure, renderer source and derived
plot hashes plus the complete display mapping. All methods/seeds remain in the
results tables; the complete CSV tables and raw saved artifacts accompany the
report. Inspect figure and table labels at the
actual PDF scale, and adjust source layout if needed without altering evidence.

## Pinned compiler

Official release: `tectonic@0.17.0`,
<https://github.com/tectonic-typesetting/tectonic/releases/tag/tectonic%400.17.0>.
Windows x86-64 MSVC archive:
`tectonic-0.17.0-x86_64-pc-windows-msvc.zip`.
The GitHub release API's published SHA-256, verified against downloaded bytes,
is `f61ce51f0b0ade1015b7de7ef368541c5424e9756ecbd0d7af97d6d48030845f`.
Keep the executable, archive and caches under ignored `.local/` or outside the
public repository. The executable license and third-party terms remain upstream.

## Typesetting fixture and verification

`layout-fixture.tex` defines an explicit layout-only flag. It imports no final
outcomes and labels the title/results section accordingly. Its PDF is an
intermediate layout check, never a completed research deliverable.

From this directory:

```powershell
..\..\.local\tools\tectonic-0.17.0\bin\tectonic.exe --keep-logs --outdir tmp/pdfs/layout-fixture layout-fixture.tex
..\..\learning\.venv\Scripts\python.exe -m unittest test_build_report -v
```

Create the temporary output directory first if absent. Once final results exist,
inspect the actual final compiler log, render every page with `pdftoppm`, inspect
PNGs, check equation/table/figure clipping, verify the text and page count with
`pdfinfo`/`pdftotext`, and confirm hyperlinks/provenance. Aim for roughly 10--16
pages with readable mathematics, rather than reducing figures to unreadable
thumbnails. The builder does not claim visual QA or remote publication.

The PDF skill was read, bundled runtime paths located, and the PDF artifact
operation marker executed successfully before PDF authoring. The final export
must still undergo fresh visual verification after real results are inserted.

For presentation-only regression checks, run `python -m unittest discover -s
research/report -p 'test_paper_figures.py' -v` from the repository root. Fixtures
contain explicitly synthetic 32-method/four-regime/three-endpoint values and
unavailable states. Synthetic rendering requires the explicit fixture flag and
labels every plot. QA sources/output stay under ignored `research/report/tmp/`,
never actual `generated/` or `output/pdf/` paths.

Initial presentation QA used a clearly labeled six-page synthetic
tables/figures booklet. It remained outside final artifact paths. All 32
methods, 15 effect rows and unavailable states were retained while footer
spacing and endpoint columns were corrected.

Final QA on 17 November 2026 rendered and inspected **all 15 actual A4 pages**,
including equations, both outcome panels, all paired effects, complete tables,
the post-hoc best/last illustration, citations and provenance. The final TeX
log has no overfull, underfull, undefined-reference or missing-character
warnings. Extracted text and 17 public hyperlinks were also checked. The
reviewed PDF SHA-256 is
`9707874403943d3e6f7ed0adc6e6d395f41286005c0a9a6fef6053fcf90efe72`.
The source/data figures and their recorded hashes remain the separate
traceability evidence; successful typesetting does not prove mathematics.
