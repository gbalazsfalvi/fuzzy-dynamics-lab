# Deterministic release data and portfolio evidence

The v0.1.0 data and evidence archives identify measured commit
`253c2de108991eef56ad2f9c0294d43ad74edeb7` (`study-source-v1`). The release app
adds reviewed dependency-metadata checksums for cold full builds without
rewriting the measured fingerprint or outcomes. Use the exact measured checkout
for full saved-study replay. See the [restoration guide](REPRODUCIBILITY.md#restore-and-replay-the-published-study)
and the separate `release-build-provenance.json` release asset for the two
source identities, reviewed additions, CI and asset hashes.

`tools/package_release.py` uses only the Python standard library. It packages
existing final files; it does not run a numerical job, replay, analysis, model
fit, selection, Git command, upload or deployment. Never run it as a substitute
for the completed frozen pipeline or claim its byte checks prove mathematics.

## Prerequisites

Complete the validation replay, pre-result selection/freeze, final evaluation,
final Java saved-artifact replay, fixed saved-artifact analysis and study review.
Leave all inputs unchanged while packaging. Required names are explicit in the
script; an interrupted validation directory cannot satisfy them.

After the final evaluation is complete, create its real replay receipt with:

```powershell
.\learning\.venv\Scripts\python.exe tools/replay_evaluation.py
```

This standard-library wrapper first checks the complete frozen cohort, then
streams and logs the actual Gradle `--console=plain verifyEvaluation` output.
It requires exit code zero and exactly one complete
`Verified N saved numerical runs, all declared outcomes, and diagnostics.` line,
where N equals the eligible payload count. Manifest, freeze and locked-protocol
bytes must remain unchanged across execution. Only then is a PASS receipt
written atomically. The receipt records schema, UTC completion time, command,
exit code, matching fingerprints, eligible count and the stored log's SHA-256.
No manual PASS JSON is needed or inferred from file existence.

The prior active receipt is preserved as a uniquely named
`evaluation-replay.previous.<UTC-id>.json` in the same ignored verification
directory before a new attempt. Any failed preflight, verifier exit, missing or
wrong-count marker, or changed input leaves no stale active PASS receipt. Logs
and history remain local; the release allowlist includes only the active
matching receipt. An exclusive run lock prevents concurrent wrappers from
retiring each other's receipts. The Windows command is a fixed argument list
through `cmd.exe /d /c gradlew.bat`; Unix executes `./gradlew` directly and
requires its executable bit. `--root` may select another explicit project root.

- `artifacts/validation/manifest.json`, its five CSV files, `summary.md` and every
  eligible `runs/*.json` or `runs/*.json.gz` payload named in the manifest.
- The corresponding complete files under `artifacts/evaluation/`.
- `artifacts/evaluation-analysis/analysis.json`, `report.md`, three analysis CSV
  summaries and all five figures as PNG/SVG/PDF.
- `artifacts/learning/training-summary.json`, `java-parity.json`, and the labels'
  train/validation CSV, summary and generation timing under `labels/`.
- `research/evaluation-freeze-v1.json`, frozen `evaluation-locked-v1.json`,
  `evaluation-selection-v1.json`, `validation-v1.json`, the base split protocol,
  six frozen models, parity fixture and locked Python requirements.
- The explicit source/document/licence allowlist and real replay receipts.

The validation receipt is
`artifacts/verification/validation-replay.json`, with matching `manifestSha256`,
`sourceSha256` and `protocolSha256`. The final receipt defaults to
`artifacts/verification/evaluation-replay.json`, or a different project-relative
path supplied with `--evaluation-replay-receipt`. It must additionally contain
`"status": "PASS"` and an integer `verifiedRunCount` equal to
the number of eligible run payloads; unavailable rows are not replayed payloads.
The replay wrapper records these facts only after observing the real command's
successful output. Packaging never invents a receipt or infers success merely
from file existence.
Keep command paths relative and exclude operational account/server details.

### Verified analysis and direct publication gate

Use the unfrozen standard-library preflight instead of changing the already
frozen analysis implementation:

```powershell
.\learning\.venv\Scripts\python.exe tools/analyze_verified.py --verify-only
.\learning\.venv\Scripts\python.exe tools/analyze_verified.py
```

The first command only checks provenance/cohort identity. The second performs
the same checks and then invokes unchanged `analysis/evaluate.py` with the same
Python interpreter. It does not retrain, regenerate data or change statistical
code. Optional flags select `--root`, `--manifest`, `--freeze`, `--receipt`,
`--protocol`, `--output`, and an explicit `--training-cost` file. Paths must stay
inside the selected project. A supplied manifest must be named exactly
`manifest.json`, matching the frozen analyzer's directory-based input contract;
an approved alias cannot cause a different sibling manifest to be analyzed.
Missing, duplicate-key, nonfinite or malformed JSON
is rejected. No final command is run by the synthetic regression tests.

Reusable `tools.analyze_verified.validate(...)` requires the actual successful
receipt to match the manifest's raw byte SHA-256, source fingerprint, protocol
fingerprint and eligible count. It checks the raw locked protocol bytes against
`freeze.protocolFileSha256` and compares the entire parsed
`research/protocols/evaluation-locked-v1.json` object with `archive.protocol`,
including every key/null, base grouping, timing flag and cohort definition.
JSON decimal numbers are compared exactly and boolean/null types remain distinct.
It also checks complete paired
outcomes, timing counts and safe payload paths. This closes a publication
boundary that merely matching claimed fingerprint labels would not establish.

Both the direct PDF builder and ZIP planner call this same gate before accepting
final evidence. Downstream payload hashes, numerical replay and analysis checks
remain required: the preflight does not independently re-prove geometry or
authenticate an operator-written receipt. It never creates that receipt.

## Run

From the project root after those prerequisites:

```powershell
.\learning\.venv\Scripts\python.exe tools/package_release.py
```

A plain CPython 3.13 interpreter also works; the tool imports no scientific
packages. Optional flags are `--root`, `--output-dir`,
`--evaluation-replay-receipt`, `--source-revision <exact-40-hex-commit>` and
`--max-curated-mib` (20 by default). Supply a Git revision only when it actually
identifies the intended source release. Without one the bundle explicitly
identifies the saved source/build fingerprint rather than inventing a commit.

Default outputs under `output/release/` are:

- `fuzzy-dynamics-lab-v0.1.0-data.zip`: complete saved evaluation and validation
  cohort metadata, all five raw CSVs for each, every eligible hashed numerical
  payload, final analysis/figures, train/validation label CSVs and reports,
  frozen models/protocols, study source metadata and selected method documents.
- `fuzzy-dynamics-lab-v0.1.0-evidence.zip`: curated **partial raw evidence** for
  the portfolio. It retains complete original evaluation/validation manifests,
  outcome/timing CSVs, analysis summaries/figures, frozen inputs and selected
  numerical examples. It omits the other raw payloads, diagnostic/refinement
  CSVs and train/validation label CSVs. The small aggregate learning reports
  remain. It cannot support a complete numerical replay by itself.
- An outer `SHA256SUMS` covering both ZIP files.

The complete study PDF is a separate reviewed release/portfolio asset. These
bundles do not gather arbitrary PDFs; only the explicitly named original
analysis and paper-layout figures are included. Downloaded/source-paper PDFs are excluded.

Both bundles explicitly include the actual report's derived traceability files:
`research/report/generated/results.tex`, `evidence.tex`, and
`output/pdf/figures/paper-layout-provenance.json`, plus PDF/PNG versions of
`paper-outcomes-1`, `paper-outcomes-2` and `paper-effects`. No generated directory
is collected wholesale. The packager rejects synthetic/stale layout provenance
and verifies its analysis, renderer, all 15 original-figure and six paper-figure
hashes. Synthetic layout QA stays under ignored `research/report/tmp/`, outside
every package path. These layouts use saved summaries without re-estimating,
resampling or selecting; original statistical figures remain unchanged.

Each ZIP has `BUNDLE_README.md`, `CURATED_SELECTION.json`, and an inner
`SHA256SUMS` covering every member except itself. The unchanged manifests still
reference raw files missing from the curated bundle; that is intentional and
documented, not a claim of completeness. The README points readers to the full
data asset on the intended v0.1.0 source release.

## Curated example rule

The rule is fixed by identifiers and the pre-result validation selection,
without consulting final statuses, costs or timings:

1. Take the lexically first problem ID in each of the four test regimes.
2. Retain its validation-frozen primary deterministic method, every retained
   primary learned-family model, random-seed-0, alpha grid and published PWL core.
3. Include each selected available run's original payload. Keep selected
   unavailable rows in the unchanged manifest and selection list without
   fabricating a file.

`CURATED_SELECTION.json` records the selected IDs, included/complete payload
counts and every omitted full-bundle path. All seeds and failures remain in
complete manifests and analysis tables. These few raw examples illustrate the
format; they are not a success-selected or representative statistical sample.
Exceeding the explicit curated size budget aborts packaging instead of silently
removing inconvenient evidence. Review any changed rule as a versioned
publication decision, without changing the frozen scientific analysis.

## Validation and public-file boundary

The packager checks complete unique problem-method-tolerance coverage, supported
statuses, absent payloads/timings for unavailable methods, individual stored-byte
hashes and bounded JSON decoding. CSV outcome identity/status and timing counts
must agree with each manifest. The actual 120-test-base/24-validation-base,
32-method design is required for a release; synthetic test fixtures cannot pass
the final planner. Source/protocol/freeze/analysis and selection/model/label
fingerprints must agree. A qualified reference lower-bound violation blocks the
release. None of this replaces Java's numerical replay or the analysis loader.
The saved analysis input-hash set must contain exactly the manifest, all five
CSV files and every eligible numerical payload path. Omitting a payload hash or
adding an undeclared path is rejected; all those stored hashes are verified.

Only the exact allowlist is read into archives. Unrelated directories, private
work, `.local/`, environments, dependencies, source-publication caches,
interrupted validation, deployment records, SSH files and the private publication
handoff are excluded. Recognizable private keys/tokens/credential fields and
local account paths in allowlisted text or decoded run JSON cause refusal with
the filename only; matching values are never printed. This targeted scan is a
safety check, not a guarantee that every possible sensitive fact is detected.
Review the actual archive member list and contents before publication.

ZIP entries have sorted POSIX paths, fixed 1980 timestamps and regular-file
permissions. Original bytes are streamed and preserved, including existing gzip
payload bytes. Every archive is reopened, CRC-tested and checked against all
inner SHA-256 values before being exposed under its final filename. Identical
inputs produce identical ZIP bytes with the same Python/zlib implementation;
different compression implementations may produce different ZIP bytes while
retaining identical member hashes. Zip64 supports large full cohorts.

Existing final or partial archive names are never overwritten. A failed attempt
removes only its newly created `.part` files. Keep existing releases and inputs.
If a deliberate new package is needed, use a fresh output directory and compare
its manifest/checksums before replacing any published download.

## Focused regression checks

```powershell
.\learning\.venv\Scripts\python.exe -m unittest discover -s tools -p 'test_package_release.py' -v
.\learning\.venv\Scripts\python.exe -m unittest discover -s tools -p 'test_replay_evaluation.py' -v
```

Fixtures are explicitly synthetic and live only in temporary directories. Tests
cover traversal/absolute names, missing final inputs/payloads, incomplete cohorts,
unavailable rows, selection invariant to success, secret-safe errors and stable
ZIP bytes/checksums after input mtime changes. No actual final data is loaded by
these tests. Test success does not mean the real release has been packaged or
published; record the real invocation, resulting sizes/hashes, and manual archive
review separately.
