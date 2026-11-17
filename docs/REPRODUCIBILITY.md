# Reproducing the software and study

Run commands from the repository root. The examples use Windows PowerShell; replace `.\gradlew.bat` with `./gradlew`, `.\learning\.venv\Scripts\python.exe` with `learning/.venv/bin/python`, and `npm.cmd` with `npm` on Linux or macOS. Build the current release for interactive use; restore the exact measured checkout below when replaying the published study.

## Environment and artifact identity

The supported development environment uses JDK 21, Node.js 24 with npm, and Python 3.13. The repository pins Gradle 9.2.0 and its distribution checksum, Gradle dependency locks and verification hashes, the npm lockfile, and hash-required Python dependencies. A TeX compiler is needed only to export the study PDF; the [report guide](../research/report/README.md) documents Tectonic 0.17.0.

Record `java -version`, `node --version`, `npm.cmd --version`, and `python --version` alongside any newly generated measurements. Timing depends on the machine, JVM, warmup, and external load; a numerically identical replay does not promise identical wall-clock observations.

To inspect a published study, use its frozen model files and downloaded evidence. To run a new study, use a separate checkout/output area and a new protocol version where necessary. Do not silently replace release weights with a retrained fit or edit source fingerprints to make an old artifact pass. The final-run guard deliberately rejects existing observation files, including partial runs, so a failed/interrupted study needs an explicitly preserved history and a fresh authorized run location.

## Build and interactive use

```powershell
.\gradlew.bat --dependency-verification=strict clean build
.\gradlew.bat :api:run
```

Open [http://127.0.0.1:4180](http://127.0.0.1:4180). Gradle installs the locked frontend dependencies with `npm ci`, runs the TypeScript/Vite build, and includes `web/dist` under the API's static resources. Python is unnecessary for this path. The standalone API process uses the application plugin; the task is `:api:run`, not `bootRun`.

The root build runs Java tests, formatting checks, Javadoc, coverage checks, frontend behavioral unit tests, and packaging. API tests include actual HTTP job/error/origin behavior and real numerical solves. Large training/evaluation runs are not default build tasks.

## Small numerical evidence

```powershell
.\gradlew.bat foundationDemo caeDemo
.\gradlew.bat verifyCaeDemo
.\gradlew.bat comparisonDemo
.\gradlew.bat verifyComparison
```

`foundationDemo` writes `artifacts/foundation-demo.json`; `caeDemo` writes `artifacts/cae-demo.json` and its Markdown summary. `verifyCaeDemo` reads the saved artifact in a separate process, reconstructs coverage, recomputes both snapshots, and replays the selection trace. It does not regenerate the file.

`comparisonDemo` is the exploratory pilot, using `research/protocols/milestone-03-pilot-v1.json` and `artifacts/comparison`. `verifyComparison` checks its saved complete cohort and numerical payloads. It is more work than the small foundation/CAE demonstrations and is distinct from final evaluation.

## Locked Python environment and fixture tests

```powershell
python -m venv learning/.venv
.\learning\.venv\Scripts\python.exe -m pip install --require-hashes --only-binary=:all: -r learning/requirements.lock
.\learning\.venv\Scripts\python.exe -m unittest discover -s learning -p 'test_*.py' -v
.\learning\.venv\Scripts\python.exe -m unittest discover -s analysis -p 'test_*.py' -v
```

Use Python 3.13 to create the environment. The Python tests use synthetic fixtures; they do not execute the final numerical cohort. Training fixes BLAS-backed worker counts before importing the numerical stack. The scripts and lockfile record the estimator/runtime provenance.

## Generate labels and train a new model artifact

This stage deliberately generates data and replaces model export files. Skip it when checking a release's frozen models and saved results.

```powershell
.\learning\.venv\Scripts\python.exe learning/freeze_manifest.py
.\gradlew.bat learningDemo
.\learning\.venv\Scripts\python.exe learning/train_ranker.py
.\gradlew.bat modelParity
```

The split manifest is `research/protocols/learning-and-evaluation-split-v1.json`. Its generator checks deterministic definitions through normalized text, not byte-identical files across platforms: native line endings can change the file-byte SHA-256. For release replay, preserve the supplied manifest/model bytes and skip generation/training; the raw manifest file hash in the training summary and evaluation freeze is the artifact identity. Java exports labels only for 64 training and 24 validation base problems into `artifacts/learning/labels`, with exact signed trial gains and numerical work records. The 120 final base problems are not labeled.

`train_ranker.py` fits training-only transforms, ridge, and five fixed seeded MLPs. It writes explicit JSON weights and `parity-cases.json` under `learning/models`, plus `artifacts/learning/training-summary.json`. `modelParity` compares actual fitted Python predictions with Java inference and writes `artifacts/learning/java-parity.json`. This checks the heuristic's export, not solver quality or certificate validity.

## Solve-level validation and selection

```powershell
.\learning\.venv\Scripts\python.exe learning/freeze_evaluation.py validation
.\gradlew.bat validationDemo
.\gradlew.bat verifyValidation
```

Validation compares all 32 methods on the 24 validation base problems. Its output is `artifacts/validation`; the successful Java replay creates `artifacts/verification/validation-replay.json`. Complete unique paired coverage and a matching replay receipt are required by the selection script. Model-family and deterministic-comparator selection use validation certification rate, then median map interval work, with fixed tie handling. MLP seeds are retained as a family.

Do not change numerical source, experiment source, Gradle build inputs/locks/verification metadata, models, or protocols while validation or final execution is running. The runner checks the source-input digest at the beginning and end. Freeze the final analysis code and plan before the next stage.

## Freeze, execute, and replay the final cohort

These are deliberate large research runs. They are not required to use the workbench, build the repository, or inspect downloaded evidence.

Allow substantial CPU time for the complete study and its separate replay. Exact rational scalar-reference diagnostics at longer logistic horizons can take much longer than a numerical solve. A problem's progress line appears only after all declared method outcomes and its diagnostics are recorded; a quiet console during that work does not imply that the process has stopped. The small interactive presets, screenshots and downloadable study provide a quicker first inspection.

```powershell
.\learning\.venv\Scripts\python.exe learning/freeze_evaluation.py final
.\gradlew.bat freezeEvaluation
.\gradlew.bat evaluationDemo
.\learning\.venv\Scripts\python.exe tools/replay_evaluation.py
```

The selection artifact is `research/protocols/evaluation-selection-v1.json`, the exact executable protocol is `research/protocols/evaluation-locked-v1.json`, and the pre-run provenance record is `research/evaluation-freeze-v1.json`. Existing frozen versions cannot be silently replaced. Final execution requires matching numerical source, protocol, model and analysis inputs, and an empty output directory.

The fixed design has 120 base problems, 32 methods, and tolerance `0.1`: 3,840 declared outcome rows, including unavailable methods. Four regimes each contain 30 base problems. Twelve bases are selected in advance for one warmup and five timed repetitions per eligible method. Other bases have one unwarmed observation and are excluded from timing comparisons. All bases remain in certification and algorithm-work comparisons.

Final evidence is written under `artifacts/evaluation`. The manifest identifies the complete paired cohort and individually hashed `runs/*.json.gz` numerical payloads. The payload hash covers the actual stored compressed bytes. Readers verify that hash before bounded decoding. Best and last CAE partitions, resource exits, raw method/model seeds, operation counts, timing order and unavailable comparisons remain explicit. A successful saved-artifact replay is required before reporting the results.

`tools/replay_evaluation.py` runs the actual Gradle `verifyEvaluation` task. It requires its successful exit, exact verified-run count and unchanged input bytes before atomically recording `artifacts/verification/evaluation-replay.json`. A previous receipt is preserved as history before a new verification starts; a failed attempt cannot leave that previous PASS active. Keep the generated log as an operator record. This is observed replay provenance, not an authenticated mathematical attestation.

## Analyze saved artifacts and build the report

```powershell
.\learning\.venv\Scripts\python.exe tools/analyze_verified.py
```

The publication preflight checks the successful replay receipt, exact locked protocol file bytes and embedded grouping/timing/cohort before invoking the unchanged frozen `analysis/evaluate.py`. The defaults read `artifacts/evaluation` and `research/evaluation-freeze-v1.json`, and write `artifacts/evaluation-analysis`; `--verify-only` performs admission without analysis. Use `--manifest`, `--freeze`, `--receipt`, `--protocol` and `--output` for explicit paths inside a selected project. The manifest must retain its actual `manifest.json` name.

Analysis reads artifacts only: it does not train or rerun numerical methods. It checks full paired coverage, CSV/manifest consistency, timing flags, exact values, analysis hashes, and payload hashes, then writes complete tables and five standalone figures. Direct `analysis/evaluate.py` remains available with `--input`, `--freeze` and `--output`, but trusts the separately completed Java replay for typed protocol identity; use the verified wrapper for publication. The report builder and ZIP packager also enforce that prerequisite. See the [analysis guide](../analysis/README.md) and [analysis plan](ANALYSIS_PLAN.md).

To import verified saved outputs and export the PDF, follow the [report guide](../research/report/README.md). With an installed compiler, the formatter accepts:

```powershell
.\learning\.venv\Scripts\python.exe research/report/build_report.py --compiler PATH_TO_TECTONIC
```

Replace `PATH_TO_TECTONIC` with the actual executable. Without `--compiler`, the script generates the evidence/result TeX includes only. Final PDF publication also requires page-by-page rendering and visual review; a successful compile alone does not establish readable figures, equations, or links.

## Frontend and browser checks

```powershell
cd web
npm.cmd run typecheck
npm.cmd run format:check
npm.cmd test
npm.cmd exec playwright install chromium
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:4180'
npm.cmd run test:e2e
```

Keep the packaged API/workbench running in another terminal. The numerical browser flow runs real API jobs and reads the installed frozen summary. Five separate lifecycle fixtures exercise reconnect/expired-job states without fabricating numerical results. Checks cover physical chart-label size, clipping and overlap after resizing. They write screenshots under `web/artifacts/screenshots`. The optional Vite development server and its carefully scoped loopback proxy are documented in the [workbench guide](WORKBENCH.md).

## Release artifacts and checksums

The [v0.1.0 release](https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/releases/tag/v0.1.0) identifies the source revision, [study PDF](https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/releases/download/v0.1.0/fuzzy-dynamics-lab-study.pdf), [curated evidence](https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/releases/download/v0.1.0/fuzzy-dynamics-lab-v0.1.0-evidence.zip), and [complete data archive](https://github.com/gbalazsfalvi/fuzzy-dynamics-lab/releases/download/v0.1.0/fuzzy-dynamics-lab-v0.1.0-data.zip). Follow the release's file inventory and checksums when restoring saved paths; the ignored `artifacts` directory is not a substitute for an explicit release inventory.

Source/build-input hashes bind the recorded numerical implementation inputs; protocol hashes bind the full declared configuration, including frozen model contents. Model exports identify training CSV and training-code/dependency hashes. Payload hashes bind stored geometry bytes; the final freeze separately records analysis and model-file hashes. Hash consistency detects changes relative to a trusted record, but provides no signature or independent authentication of correctness.

### Restore and replay the published study

The measured source is commit `253c2de108991eef56ad2f9c0294d43ad74edeb7`, tagged `study-source-v1`, with source/build fingerprint `5fe3555edfc33b1e475e6b0af908818e4495b7931361e345b262e25a5a7cb76c`. The release app subsequently adds 17 missing POM/module metadata checksums required by a cold full API/test build. Strict verification remains enabled; no version, executable dependency, numerical source, model, protocol or statistical analysis changed. The [measured input inventory](../research/provenance/measured-build-inputs-v1.json), [original metadata](../research/provenance/measured-verification-metadata-v1.xml), and [reviewed additions](../research/provenance/dependency-metadata-review-v1.json) preserve that distinction. The original data/evidence ZIPs retain their measured-source revision and bytes. The separately published `release-build-provenance.json` identifies the release revision and its CI checks.

Create a separate checkout, leaving an existing workbench checkout intact:

```powershell
git clone --branch study-source-v1 https://github.com/gbalazsfalvi/fuzzy-dynamics-lab.git fuzzy-dynamics-lab-study
cd fuzzy-dynamics-lab-study
git rev-parse HEAD
```

Require the exact 40-character commit above. Download the **complete data ZIP** and `SHA256SUMS` from release v0.1.0. Check the ZIP's SHA-256 against that inventory before extraction; the small curated ZIP deliberately omits most numerical payloads and cannot support full replay. Extract the ZIP's contents into this measured checkout, retaining the relative `artifacts/`, `research/`, `learning/` and other recorded paths. It includes documents from the measured snapshot; do not overwrite the checkout with current-release source files. Check its internal `SHA256SUMS` and inventory as described in `BUNDLE_README.md`. Keep paths as regular files, without links or traversal entries.

With JDK 21, run:

```powershell
python tools/replay_evaluation.py --root .
```

This standard-library command archives the previous active receipt, executes the real Java verifier for all 3,700 eligible saved numerical runs and complete declared outcomes, and writes a new PASS receipt only after observed success. It does not regenerate outcomes or train models. The measured checkout's main-runtime numerical path supports strict dependency verification; CI separately checks its small foundation/CAE generation and saved CAE replay from an empty dependency cache. The current-release CI builds the full API, tests and workbench. Neither small CI job is represented as a full cohort replay.

To analyze again, create the hash-locked Python 3.13 environment in this measured checkout and run `tools/analyze_verified.py` as above. Full replay and analysis use the original frozen fingerprint; a maintenance build's different digest must not be inserted into the saved manifest, freeze or receipts. Retraining, timing reproduction, independent proof review, and external validation remain separate activities.
