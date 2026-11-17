# Frozen refinement policies

This directory trains and exports empirical cell-ranking heuristics for Certified Adaptive Extension. The [model card](MODEL_CARD.md) documents the 16-feature recipe, 8,536 labels from 64 training and 24 validation groups, all optimizer warnings, exact frozen model hashes, and solve-level validation selection. Scores affect refinement order only; the Java interval engine owns every certificate calculation.

## Reuse the release models

The six inference exports are `models/ridge.json` and `models/mlp-seed-{11,23,37,51,71}.json`. Their feature version is `cae-cell-features-v1`. Keep `models/parity-cases.json` with them. No Python environment, pickle loader, or machine-learning service is required for Java inference.

From the repository root, use JDK 21 and Node.js 24 to start the packaged workbench:

```powershell
.\gradlew.bat :api:run
```

Open [http://127.0.0.1:4180](http://127.0.0.1:4180), select a ranked strategy and an available model ID, and run a bounded local experiment. The API loads validated exports from `learning/models` at startup; restart after intentionally changing model files. The API accepts model IDs, not client-supplied files or paths.

To check the stored Python prediction fixtures against Java inference without retraining:

```powershell
.\gradlew.bat modelParity
```

This writes `artifacts/learning/java-parity.json`. The recorded release check covered 576 predictions across all six models with maximum absolute difference `5.342948306008566E-16`. Export parity is distinct from solve quality and numerical bound verification.

## Explicitly generate a new trained artifact

Training replaces model export files. Do it in a separate research checkout/output area when studying a new fit; do not retrain while reproducing a release's frozen decisions or while validation/final execution is active. A repeated seed does not make a newly trained model the same artifact.

Use Python 3.13 and run from the repository root:

```powershell
python -m venv learning/.venv
.\learning\.venv\Scripts\python.exe -m pip install --require-hashes --only-binary=:all: -r learning/requirements.lock
.\learning\.venv\Scripts\python.exe -m unittest discover -s learning -p 'test_*.py' -v
.\learning\.venv\Scripts\python.exe learning/freeze_manifest.py
.\gradlew.bat learningDemo
.\learning\.venv\Scripts\python.exe learning/train_ranker.py
.\gradlew.bat modelParity
```

On Linux/macOS, use `./gradlew` and `learning/.venv/bin/python`. The manifest generator checks deterministic definitions through normalized text; native line endings can change regenerated file bytes. Preserve the release's supplied manifest/model bytes and skip this stage when replaying its recorded artifacts. Java label generation skips every held-out group and records signed trial gains and numerical work. The Python loader checks data hashes and allowed base IDs; feature and target normalization are fitted on training rows only.

The trainer writes explicit ridge and five one-hidden-layer tanh MLP exports plus actual-estimator parity fixtures. Data and training evidence are written under `artifacts/learning`; the smaller inference artifacts live under `learning/models`. All five recorded MLP fits reached the fixed 1,200-iteration limit and retained their convergence warnings. Inspect those warnings instead of silently extending the budget after seeing comparison outcomes.

## Validation, freeze, and evidence

Solve-level validation selected maximum-bound as the primary deterministic comparator and ridge as the primary learned family before final outcomes. The [model card](MODEL_CARD.md) gives the validation rates, work medians, and hashes. All five MLP seeds and the other declared methods remain in the complete comparison; no best-seed model is substituted.

The [reproducibility guide](../docs/REPRODUCIBILITY.md) provides the exact validation → Java replay receipt → selection → final freeze → evaluation → replay → saved analysis sequence. These are deliberate research runs, not default build steps. Existing final observations are preserved rather than overwritten, and numerical source, models, protocols, and analysis remain fixed during the frozen study.

For saved records, use the release's evidence/data archives and their inventory. Important paths are `artifacts/learning/labels/summary.json`, `artifacts/learning/training-summary.json`, `artifacts/learning/java-parity.json`, and the [evaluation freeze](../research/evaluation-freeze-v1.json). Hashes identify inputs and exports; they do not establish authenticity, scientific novelty, physical applicability, or external mathematical review.
