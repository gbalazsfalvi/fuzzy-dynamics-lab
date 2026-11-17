# Numerical and experiment implementation review

Review date: 17 November 2026. Scope: a focused source review before the first held-out execution, following separate work on the API. No final outcomes were inspected, no large cohort was executed, and no production code was changed by this review. The review is an implementation audit, not an independent mathematical proof or a scientific novelty assessment.

This is a historical review snapshot. The integration resolution at the end
records the implemented corrections; the findings below describe the
pre-correction state rather than unresolved release defects.

## Findings requiring attention before held-out execution

### 1. Important: the first final computation was not required to have a matching pre-existing freeze

At the reviewed source state, `experiments/build.gradle.kts` registered `evaluationDemo` as a direct `ComparisonDemo` invocation. Neither `ComparisonDemo` nor `ComparisonRunner.run` required `research/evaluation-freeze-v1.json` before executing a `FROZEN_HELD_OUT` protocol. The final freeze was checked later by saved-evidence verification. In addition, `EvaluationControl.freeze` rejected an existing `artifacts/evaluation/manifest.json`, but did not reject existing partial `runs/*.json.gz` files. `ComparisonRunner.Draft.record` writes those files during execution, before the archive manifest is written.

Consequently, an interrupted final run could expose held-out outcomes and still permit a later first freeze because the manifest did not yet exist. This would invalidate the claim that the complete analysis and method selection were frozen before any final outcome. It does not alter the validity of an individual CAE bound.

Minimal verification: traced `evaluationDemo` → `ComparisonDemo.main` → `ComparisonRunner.run` and the writes in `Draft.record`, then compared the only pre-existing-output test in `EvaluationControl.freeze`. No final run was started to demonstrate the problem.

Required correction: reject final computation before any reference preparation or numerical work unless the freeze matches the protocol, source, models, and analysis inputs; require a fresh empty final output location; reject a first freeze after any final output exists, including partial raw runs. Test these failures using tiny synthetic protocols and temporary directories.

Status at review handoff: the implementing agent confirmed this correction was being added with tests first. Verification of that subsequent correction is outside this review snapshot. No held-out cohort had been started according to the implementing agent.

### 2. Important: validation selection did not itself require the complete paired cohort

`learning/freeze_evaluation.py:select_on_validation` computes each method or family's score from whichever matching rows are present. It rejects an entirely absent method, but does not check the exact expected problem × method × tolerance cohort, duplicate rows, balanced family seed coverage, or agreement with the frozen validation protocol. Its CLI checks the stage and the `validation-` problem-ID prefix, which do not establish those properties.

Missing or duplicated rows can therefore alter the pre-final comparator/family choice without being rejected. This is a selection admission gap, not evidence that the current normally generated validation artifact is incomplete or that final data entered training. `ComparisonRunner` normally writes its manifest only after completing its declared cohort, and Java replay separately checks full coverage; the Python selection entry point does not require proof that replay occurred.

Minimal verification, executed without reading outcomes: imported the function with `runpy`, supplied one synthetic `validation-000` row for each of the four deterministic methods, ridge, and the five MLP seeds, and observed successful selection of ridge. This ten-row synthetic input is smaller than the declared complete validation cohort. No files were modified by the probe.

Required correction: before selection, validate exact protocol identity and unique complete paired coverage, including every declared seed; retain the verified validation manifest hash in the selection artifact. A synthetic missing/duplicate-row regression should fail before scores are computed.

Status at review handoff: reported to the implementing agent; correction and its verification remain pending in this review snapshot.

## Numerical paths inspected without a new counterexample

The following observations describe the inspected source and its explicit trust boundary. They are not claims of exhaustive correctness.

* `cae/CaeCellEvaluator`, `CellBounds`, and `CertificateVerifier`: the comparison region includes both the reachable box and numerical representative; the interval image is intersected with a separately valid propagated ball; upper-bound calculations round upward. Membership variation and representative evaluation error remain separate from spatial propagation. The grade cap uses the common zero baseline. The snapshot verifier checks context, exact binary prefix coverage, resource shape, every recomputed cell, and the aggregate per-time envelope. It shares the canonical cell recipe, so independent analytic references remain necessary.
* `cae/CaeSolver` and `ActivePartition`: both child computations finish before replacement. Both external ranking scores are also computed before the leaf map or envelope is mutated. Arithmetic/null-score failure preserves the complete parent partition. The earliest best bound and last complete partition remain distinct; periodic FIFO fairness is retained under ranked selection. Existing `RankedSolverTest` and `CaeSolverTest` explicitly cover these rollback cases; this review read those tests but did not rerun the whole build.
* `ranking/CellFeatures`, `DenseRankingModel`, and `SeededPriority`: empirical floating-point scores influence selection only. Bounds remain in the interval engine. Feature names/version, scaler dimensions, finite weights, layer shapes, and output activation are checked. Seeded random priorities are described accurately as per-path priorities, rather than uniform active-leaf sampling. Finite inference overflow is rejected rather than inserted into a certificate.
* `cuts/IntervalUnion` and `ActivePointCoverage`: interval gaps are retained. Directed distance extrema are evaluated at source endpoints and target-gap midpoints lying in the source; that covers the maxima of the piecewise linear distance-to-target function. Incremental point insertion removes the old neighboring gap and inserts its replacements. No counterexample was identified to these rules.
* `cuts/AlphaGridEndograph`: the forward direction activates atoms in decreasing grade order and minimizes the maximum of the resulting spatial coverage radius and vertical penalty. This is equivalent to searching the radius threshold that makes all source points match an eligible atom. The reverse direction examines every finite atom against all stored cut segments, with the full common baseline in both directions. `AlphaGridEndographTest` contains finite exact-metric parity and continuous-interval cases; no incorrect interval-union distance was identified in the inspected implementation.
* `cuts/ExactAlphaCuts` and `AlphaCutTrajectory`: scalar images use endpoint and known turning-point evaluations, retain complete unions, and propagate every positive level. The staircase's largest vertical gap is kept separate from the discrepancy to that staircase. Public constructors are correctly described as shape checks rather than numerical provenance checks.
* `pwl/PiecewiseLinearExtension`, `PiecewiseLinearState`, and `LinearSegment`: transport splits source pieces at exact map knots, preserves overlapping pieces under maximum, and represents collapsed constant-map segments as a singleton at their maximum grade. Piece-budget exhaustion discards the incomplete output and retains a complete time prefix. This is the stated exact PWL specialization, not the cited paper's full nonlinear preprocessing or timings.

## Learning, cohort, provenance, and memory observations

`LearningData` skips all test definitions when exporting labels. Trial-split targets remain signed, including regressions. `train_ranker.py` loads train/validation CSVs only, checks their digests and allowed base-group IDs, fits both feature and target normalization on training data only, and exports fixed ridge/MLP architectures and all declared seeds. Validation predictions and export-parity cases do not enter estimator fitting. `freeze_evaluation.py` selects the MLP family with all its seeds, rather than selecting a fortunate seed.

A lightweight deterministic probe of `freeze_manifest.py:build_manifest` found 208 base problems and 208 unique full mathematical definitions. This supports the inspected generated manifest's grouping separation; it is not a statistical claim about independence or representativeness. No actual final outcomes were loaded for this probe.

`ComparisonRunner` now keeps payload filenames, compressed-file digests, verification summaries, and small timing arrays in each draft instead of retaining the complete geometry of all methods. Non-pilot payloads are compressed, checked against their stored-byte digest on reload, and decompression is capped at 64 MiB. The source-input hash is captured at both run start and completion, and a mismatch rejects archive completion. These changes address whole-method payload retention and source changes during a run.

Residual memory scope: `ComparisonReports.writePayload` still materializes one full JSON string and its UTF-8 bytes; read/replay materializes one bounded decoded payload and its object graph, and deterministic comparison can temporarily hold a newly solved payload plus its reloaded counterpart. Compression does not itself establish a tight Java heap bound for every locally supplied protocol. The CSV writer also accumulates its output strings. No new out-of-memory failure was reproduced here, so this is a limitation requiring a representative bounded-memory check before the large run, not a claim that the current declared cohort necessarily fails.

Source SHA-256 records the on-disk source/build inputs, not the actual loaded JVM class bytes. The start/end equality guard detects edits during execution but cannot authenticate an independently assembled classpath or stale classes already loaded before the first digest. Use the checked Gradle task and a stable source tree for the recorded run; do not describe this digest as binary authentication. Model and analysis hashes provide separate artifact provenance.

## Release boundary

No new critical numerical certificate violation was identified in this focused review. The two pre-final admission findings above must be resolved and freshly verified before claiming a frozen held-out study. The separate full build, saved-artifact replay, Python/Java model parity, and bounded-memory execution checks remain required; this report does not substitute for them.

The P1 derivation remains an internal argument. Independent external mathematical review, complete closest-literature comparison, scientific novelty, comparative speedup, and useful arbitrary-tolerance convergence are not established by this code review or by a successful test suite. Source publication and remote deployment are separate from these local verification results.
# Integration resolution

The primary implementer reproduced both important admission findings with regression tests before applying fixes. Final execution now requires a matching pre-run source/protocol/analysis freeze and a fresh empty observation directory. Freeze creation also rejects partial final observations. Python validation selection requires the exact complete unique paired cohort and a matching Java replay receipt. Focused tests and the repository checks passed after these changes; full validation/final numerical replay remains part of the actual study execution.

