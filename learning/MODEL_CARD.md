# Frozen refinement ranking models

**Version:** v0.1.0, feature recipe `cae-cell-features-v1`.  
**Purpose:** empirical selection of initial-domain cells for CAE refinement.  
**Primary learned family selected on solve-level validation:** ridge.  
**Scope of this card:** recorded labels, training, export parity, and pre-final validation selection. It contains no held-out conclusions.

## Intended use and numerical boundary

The exported models predict a signed local horizon-bound reduction. At inference, the ranking score is `max(0, predictedGain) / estimatedChildWork`. It chooses which eligible cell to split; it never supplies membership, trajectory, radius, or certificate values. The work estimate is `2 * (3 * (T + 1) + depth + 1)` and is a fixed ranking heuristic, not an elapsed-time prediction or measured operation count.

The Java CAE engine computes directed decimal bounds independently of the ranking score. Both children and their scores must complete before a parent is removed. Creation age breaks score ties, and every fifth refinement selects the oldest eligible leaf. Frozen model inference therefore shares the complete-cover, atomic-replacement, finite-budget, best/last, and fairness rules of deterministic CAE.

An inaccurate model may waste work, miss tolerance within budget, or perform worse under distribution shift. A negative prediction is clipped only for selection; negative training labels remain in the data. No model accuracy or efficiency claim is part of the mathematical certificate. See the [CAE method](../docs/CAE_METHOD.md) and [numerical contract](../docs/NUMERICAL_CONTRACT.md).

## Data and target

The [frozen split manifest](../research/protocols/learning-and-evaluation-split-v1.json) contains 208 disjoint base problems: 64 training, 24 validation, and 120 held out across four 30-base regimes. Exact rational map and membership definitions are retained. No final test row is loaded by training.

| Label partition | Base groups | Rows |
| --- | ---: | ---: |
| Training | 64 | 6,208 |
| Validation | 24 | 2,328 |
| Total | 88 | 8,536 |

Labels come from bounded maximum-bound exploration with precision 24, tolerance `0.000001`, up to 48 refinements, 49 active cells, depth 12, and fairness period 5. Each of the 97 complete candidate cells visited per base is trial-split into two fully computed children. The target is `parent.worstBound - max(left.worstBound, right.worstBound)`.

This target measures **local** certificate-contribution reduction, not the improvement of the complete partition's global bound. Another cell can dominate the envelope; a local gain may leave the global error bound unchanged. A split may also increase its contribution, so the signed label can be negative. Trial children and target values are not input features.

The label summary records 17,072 trial cell attempts, 107,088 map images, 53,544 spatial Lipschitz queries, 34,144 membership ranges, and 17,072 membership Lipschitz queries. Exploration work is recorded separately. Source records are `artifacts/learning/labels/summary.json`, `train.csv`, and `validation.csv` in the evidence bundle.

## Ordered feature recipe

All descriptors are computed from an already complete parent cell by [`CellFeatures`](../numerics-core/src/main/java/io/github/gbalazsfalvi/fuzzy/ranking/CellFeatures.java). Here `T` is the horizon, `rho_t` the stored spatial radius, and `b_t` the cell's endograph contribution. The exported feature order is fixed:

| # | Name | Recipe |
| ---: | --- | --- |
| 1 | `dimension` | Initial-box coordinate count |
| 2 | `horizonLog` | `log(1 + T)` using `StrictMath.log1p` |
| 3 | `depth` | Binary cell-path depth divided by the fixed value 24 |
| 4 | `initialRadius` | `rho_0` |
| 5 | `grade` | Stored lower representative membership grade |
| 6 | `gradeError` | Upper bound on representative grade evaluation error |
| 7 | `variation` | Whole-cell absolute membership variation bound |
| 8 | `upperMembership` | Whole-cell membership upper bound |
| 9 | `horizonBound` | `max(b_0, ..., b_T)` |
| 10 | `nextStepBound` | `b_min(1,T)`, including time zero when `T=0` |
| 11 | `terminalBound` | `b_T` |
| 12 | `maximumRadius` | `max(rho_0, ..., rho_T)` |
| 13 | `meanRadius` | Arithmetic mean of all `T+1` stored radii |
| 14 | `maximumLipschitz` | Maximum stored incoming transition Lipschitz bound; initial value zero |
| 15 | `tighteningRatio` | Final stored radius / final direct-image radius, or zero when the denominator is zero |
| 16 | `widthRatio` | Minimum initial-axis width / maximum initial-axis width, or zero when the denominator is zero |

Decimal descriptors are converted to finite binary doubles for heuristic inference only. The two ratios use decimal division with `DECIMAL64` before conversion. No model family name, base/test identity, child result, target label, exact-reference discrepancy, or held-out outcome is included. All features must be finite.

## Training and optimizer outcomes

Feature `StandardScaler` means/scales and target mean/standard deviation are fitted on training rows only. Validation rows use those frozen transforms. A zero target standard deviation would use scale one. There is no validation-driven refit, best-seed selection, or architecture search in the training script.

The simple baseline is `Ridge(alpha=1.0)`. Each of the five MLPs has one 16-unit tanh hidden layer and a linear output, L-BFGS optimization, regularization `alpha=0.01`, `max_iter=1200`, `max_fun=100000`, and tolerance `1e-7`. Seeds are fixed at 11, 23, 37, 51, and 71.

| Model | Seed | Validation label MSE | Validation label MAE | Recorded iterations | Warning |
| --- | ---: | ---: | ---: | ---: | --- |
| `ridge` | 0 | 0.002311048082510926 | 0.03014993613659917 | Not reported | None |
| `mlp-seed-11` | 11 | 0.0007891152281489646 | 0.012687732190308085 | 1,200 | Iteration limit |
| `mlp-seed-23` | 23 | 0.0008476579277674303 | 0.013121342636476156 | 1,200 | Iteration limit |
| `mlp-seed-37` | 37 | 0.0007777318778051761 | 0.013295599317205318 | 1,200 | Iteration limit |
| `mlp-seed-51` | 51 | 0.000900284625398667 | 0.01328081172558219 | 1,200 | Iteration limit |
| `mlp-seed-71` | 71 | 0.0008250078627741358 | 0.013350435201832456 | 1,200 | Iteration limit |

The validation zero-gain predictor MSE is `0.006427966968357236`. These are label-regression metrics on correlated cells within 24 base groups, not solve-level independent trial counts or held-out performance estimates.

**All five MLP fits reported nonconvergence.** Each warning records L-BFGS status 1 after 1,200 iterations with `STOP: TOTAL NO. OF ITERATIONS REACHED LIMIT`, followed by the estimator's suggestions to increase the budget and consider scaling. The complete warning strings are preserved in `artifacts/learning/training-summary.json`. Scaling was already fitted on training data. No additional optimization was performed to remove those warnings, and convergence to an optimum is not claimed. Ridge reports no iterative count (`null`); seed zero identifies its export and does not imply a measured training-seed distribution.

The recorded environment was Python 3.13.1, NumPy 2.3.4, SciPy 1.16.3, scikit-learn 1.7.2, joblib 1.5.2, and threadpoolctl 3.6.0. BLAS-related thread variables are set to one before importing the numerical stack. The [requirements lock](requirements.lock) pins release files with SHA-256 hashes.

## Export, parity, and provenance

Each model is explicit JSON: feature version/order, training-only means/scales, target transform, input-by-output weights, biases, `TANH`/`IDENTITY` declarations, seed, and training data/code hashes. Java inference uses [`DenseRankingModel`](../numerics-core/src/main/java/io/github/gbalazsfalvi/fuzzy/ranking/DenseRankingModel.java), without a machine-learning runtime dependency in the core. The record validates the complete feature and shape contract and rejects nonfinite inference output.

Python export checks against the actual fitted estimators passed. Java replay checked 576 predictions across six models, including training, validation, and controlled stress feature vectors. The maximum absolute Python/Java difference was `5.342948306008566E-16`, within the declared absolute/relative tolerance `1e-10`. Parity verifies the exported function, not model usefulness or the numerical certificate.

| Input identity | SHA-256 |
| --- | --- |
| Frozen split manifest file bytes | `0bf5c09559d77af2f45fc3a1c969ad7ccc52bda883800e5b5dd389bfa798fc27` |
| Training CSV bytes | `80ba6fcfc1f3853928b25e27fe5398f1999be380a780da04e972008373e56924` |
| Validation CSV bytes | `e8b27d0a33163c1552c895fbd6dd7af1768c6785ecf84aecbe7079efd93aa830` |
| Training code/dependency recipe | `56f801b561778a5ea778223c8a7896d980c6344717742d60fb2aa453ff62b5a2` |

The training-code digest concatenates each filename and its bytes in this order: `train_ranker.py`, `requirements.lock`, `freeze_manifest.py`. The label summary separately records numerical source SHA-256 `8a11bbbbc5e31d10e810fbc1de25ff33ee8c8f298ec863161d09c9fb920cc327` and canonical label-protocol SHA-256 `291486a6e07cef7d79b5c19ae850b7b85a4e60d0a52cc94ddd128c340f5d1182`. A canonical protocol-object hash and a file-byte hash describe different serializations and need not be equal.

| Frozen model file | SHA-256 |
| --- | --- |
| [ridge.json](models/ridge.json) | `64d839932913a7792634feaf605113ed2725367b21adbaa019313fbe042fe209` |
| [mlp-seed-11.json](models/mlp-seed-11.json) | `2027c498e6b1f2f0ba42d7e92c4445e4dbc15242d605e800029f38bba07ae631` |
| [mlp-seed-23.json](models/mlp-seed-23.json) | `a17a95fff32e1db2da3393c008f3f979debbf992e2369fc1506dc93c82ddeaa0` |
| [mlp-seed-37.json](models/mlp-seed-37.json) | `d1dbac0c084bdadb7dece22d3c4f494a9ded06749a9b011a94483db9cb23e15a` |
| [mlp-seed-51.json](models/mlp-seed-51.json) | `7ad1385fc53c51edf880e945ea1e432ede90f7d1acaf764f667d5bb95d07f654` |
| [mlp-seed-71.json](models/mlp-seed-71.json) | `ebde7d481f0fa887796fce16142e0a95411cac6da7b8e9bb3ed43ef2a25f4c8e` |

These six file hashes, both label CSV hashes, and the training-code recipe were rechecked against the recorded training summary when preparing this card. Hashes provide consistency and artifact identity, not authentication or independent correctness review.

## Solve-level validation selection

The complete validation comparison contains 768 declared outcomes (24 bases × 32 methods), including 28 unavailable comparisons. Java replay verified all 740 eligible numerical runs. The matching receipt and manifest hash are required before policy selection.

| Candidate | Validation certification | Median algorithm map images |
| --- | ---: | ---: |
| Maximum-bound deterministic policy | 17 / 24 | 738 |
| Next-step deterministic policy | 17 / 24 | 804 |
| Ridge learned family | 16 / 24 | 838 |
| MLP family, all five seeds | 79 / 120 | 1,094 |

The predeclared rule first maximizes certification rate, then minimizes median map images. It selected **maximum-bound** as the primary deterministic comparator and **ridge** as the primary learned family. The MLP-family rate is approximately `0.6583333333333333`; all five seeds remain in the full comparison, although none is chosen individually as the primary learned method. Lower label MSE did not automatically select the neural family.

The [final pre-run freeze](../research/evaluation-freeze-v1.json) records this selection, the verified validation manifest hash `af1c6608f2a80198ec3873e9cde0bebfd80dc2bfec0d3ec0a2cfb954022c464b`, all model-file hashes, numerical source, full final protocol, and analysis hashes. This section reports validation only. It supplies no final test conclusion, runtime advantage, or uncertainty estimate beyond the declared validation scores.

## Limitations and reuse

Training uses synthetic built-in maps on the unit interval/square and tent-peak memberships, not observational or physical-system data. Label exploration has smaller budgets than evaluation. In-distribution horizons are 2/4; the declared out-of-distribution tests include horizons 6/8, narrower peaks, and shifted map parameters. More demanding horizons, precision changes, new membership shapes, or different systems may produce feature distributions not represented by training.

Local signed gain, global certificate progress, tolerance success, operation counts, and elapsed time are distinct objectives. Clipping negative predictions can create ties; FIFO fairness supplies the declared periodic exploration but does not guarantee efficiency. Fixed precision and finite resources can prevent success. The 24 validation groups and five MLP fits do not establish broad statistical generalization or independent training-seed behavior for ridge.

Reuse the exact frozen JSON weights and feature recipe when reproducing this release. A retrained fit is a new artifact even with identical seeds. Changing the feature recipe requires an explicit version/contract update. Neither Python/Java parity nor canonical certificate replay is external proof review; scientific novelty and the closest-literature full-text comparison remain unresolved. See the [learning guide](README.md), [full reproduction guide](../docs/REPRODUCIBILITY.md), and [internal review](../docs/INDEPENDENT_RESEARCH_REVIEW.md).
