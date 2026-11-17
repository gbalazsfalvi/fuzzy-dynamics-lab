# Experiment protocol

This document preserves the initial proposal. The executable final design is [evaluation-locked-v1.json](../research/protocols/evaluation-locked-v1.json); the frozen [analysis plan](ANALYSIS_PLAN.md) records the actual primary certification endpoint, grouped uncertainty, finite budgets and preselected timing cohort. Its explicitly documented decisions supersede the proposed settings below and were made before final outcomes.

**Protocol:** proposed version 1, dated 17 November 2026.  
**Status:** design to be calibrated on pilot instances and frozen before the final held out evaluation.

The primary comparison measures the total cost of obtaining a declared finite horizon endograph certificate. A secondary comparison measures discrepancy against qualified reference solutions. A certificate is a bound, not an observed error, and the two quantities will be reported separately.

## 1 Hypotheses and endpoints

| ID | Hypothesis | Primary endpoint |
| --- | --- | --- |
| H1 | Horizon aware CAE reduces the cost of reaching a given tolerance relative to uniform refinement | Time and model evaluations to a valid certificate |
| H2 | Learned selection improves on the strongest deterministic adaptive selection | Paired time to certificate, with timeouts retained |
| H3 | Verification overhead and conservative enclosures can eliminate the speed advantage | End to end runtime, certificate slack, and failed tolerance runs |
| H4 | Small endograph discrepancies can coexist with missing low grade regions | Declared alpha cut discrepancy and component diagnostics |

H1 and H2 are empirical hypotheses. H3 and H4 define required failure analyses. No target speedup is an assumed result.

The correctness requirement is zero verified certificate violations on qualified references. Any violation blocks a correctness claim until the mathematics, reference, or implementation is resolved.

## 2 Benchmark definitions

Use X = [0,1]^d with infinity norm distances.

| Family | Exact definition | Initial parameter choices | Reference role |
| --- | --- | --- | --- |
| Affine contraction | f(x) = a x + (1-a)/2 | a = 1/4, 3/4, 19/20 | Analytic trajectories and interval images |
| Tent map | f(x) = 1 - abs(2x-1) | Fixed slope two | Exact rational piecewise linear checks at manageable horizons |
| Logistic map | f(x) = r x(1-x) | r = 16/5, 37/10, 4 | Independently verified enclosures and numerical estimates |
| Coupled tent map | f1 = (1-c)T(x)+cT(y), f2 = cT(x)+(1-c)T(y) | c = 1/10, 1/4 | Exact low horizon piecewise geometry where feasible; verified enclosures otherwise |

All these maps preserve the declared domain. Prove and test that property for each implementation. For the coupled tent map, a global infinity norm Lipschitz constant two follows from the row sums of the piecewise slopes.

Proposed one dimensional horizons are 1, 2, 4, 8, 12, and 32. The horizon 32 cases are stress tests and may have uninformative bounds. Proposed two dimensional horizons are 1, 2, 4, and 8.

### Membership profiles

Use maxima of tent profiles,

$$
\mu_0(x)=\max_j h_j\max(0,1-\|x-c_j\|_\infty/w_j),
$$

with centers and widths contained in the domain. These are continuous, have computable variation bounds, and can produce nonconvex alpha cuts. Store a rational form of each selected parameter.

Profiles include a single peak, two separated full height peaks, a narrow peak, a high peak with a distant weak peak of height 1/100, and two dimensional separated peaks. A family with unequal heights tests nonnormal approximations and alpha cuts that can become empty.

Do not force a membership function to integrate to one. Do not replace separated components with their convex hull.

## 3 Comparison methods

| Method | Required implementation and scope |
| --- | --- |
| Uniform finite representatives | Same trajectory and certificate engine as CAE; isolates the value of adaptivity |
| Deterministic maximum bound refinement | Refine the cell with the largest contribution over the requested horizon |
| Deterministic cost aware refinement | Use an explicit nonlearned estimate of benefit per cost; tune only on pilot or training data |
| Random adaptive refinement | Same bisection and fairness rules; tests whether learning exceeds a weak stochastic policy |
| Learned CAE | Same certified engine with a frozen learned selector |
| Alpha cut propagation | Preserve complete interval unions in one dimension; preserve nonconvex sets in any supported two dimensional adapter |
| Published approximation methods | Reproduce the 2007 or 2016 method after full text access, and the 2021 PSO based simulation where assumptions match |

At least one relevant published method must be reproduced or run from an attributable implementation before a broad comparative research claim. An inaccessible or incompatible method is marked unavailable, with the affected claim narrowed.

The [ZEP package](https://cran.r-project.org/web/packages/ZEP/index.html) can supply an external fuzzy number comparison on eligible cases. It is not a baseline for arbitrary nonconvex states. [CORA](https://tumcps.github.io/CORA/) is adjacent reachability software; an adapter is optional and must account for its different representation and assumptions.

Published PSO comparisons must include optimization and preprocessing costs and keep the same optimization budget across methods.

## 4 Reference hierarchy

Assign every discrepancy measurement one of these labels:

1. ANALYTIC: exact formulas or exact rational piecewise geometry with checked coverage.
2. VERIFIED_ENCLOSURE: an independent computation gives an enclosure and a reference error bound.
3. CONVERGENCE_STUDY: increasingly refined numerical results with a reported difference.
4. EMPIRICAL_GRID: a dense sampled estimate without a global error guarantee.

Only the first two support a certified discrepancy claim. A high resolution grid is not called ground truth.

For an approximate reference v with dE(v, mu) at most deltaRef and an exact comparison distance dE(muHat, v), the triangle inequality gives a discrepancy interval with endpoints max(0, dE(muHat,v)-deltaRef) and dE(muHat,v)+deltaRef. Include comparison algorithm error as well if the distance itself is approximate.

A difference between two approximate solutions is insufficient to accuse a certificate of failure. The verified lower bound on the true error must exceed the certificate after accounting for all error sources.

Use an implementation or mathematical construction independent of the method under test for at least the analytic and exact cases.

## 5 Metrics

### Primary metrics

- Maximum certificate over all requested times.
- Wall time to meet a fixed tolerance, including bound verification and learned inference.
- Number of map evaluations, interval image evaluations, derivative bounds, membership bounds, and split evaluations, recorded separately.
- Best certificate at a fixed wall time or evaluation budget.
- Timeout, memory exhaustion, numerical failure, and unsupported assumption rates.

Do not hide interval or training costs by counting only point evaluations.

### Secondary metrics

- Reference discrepancy intervals at each time.
- Ratio or gap between the certificate and qualified observed error.
- Peak memory, partition size, and retained artifact size.
- Runtime spent in verification, selection, learning inference, and reporting.
- Alpha cut Hausdorff discrepancies on declared nonempty cuts.
- Empty cut mismatches and height deficits, reported separately.
- Component diagnostics under a fixed, documented output discretization.

Use alpha levels 1/200, 1/100, 1/20, 1/10, 1/4, 1/2, 3/4, and 1. The ordinary levelwise Hausdorff metric is undefined when only one of the compared cuts is empty under the usual nonempty compact set convention. Do not silently replace it with zero.

Finite representatives are isolated points. Component counts must therefore use a declared common raster or neighborhood radius rather than counting every representative as a component. Use the same spatial resolution, connectivity rule, and reference geometry for all methods. These are resolution dependent diagnostics, not a proof of topological preservation.

## 6 Budgets and pilot calibration

Proposed tolerances are 1/10, 1/20, and 1/100 in the defined normalized metric.

Initial local budgets are 1, 10, and 60 seconds, a five minute hard limit per run, one CPU execution thread for the main algorithm comparison, and a four GiB worker memory limit. Pilot runs may revise these values before the final freeze. Record the reason and create a new protocol version.

Numerical and display budgets are separate. Rendering screenshots is not part of a headless numerical timing.

Run the same point and interval implementations for the policies that share the engine. For cross language external comparisons, report both language/runtime differences and algorithmic counters.

Use a stratified suite, not the full Cartesian product of every parameter, horizon, tolerance, budget, seed, and timing repetition. The pilot will select a final suite within an initial allowance of 72 CPU hours for the main comparison, with reference generation and training budgeted separately. Fix the allocation and cohort before inspecting final results. Any necessary reduction in scope narrows the associated claim.

## 7 Data separation and training

Define a base problem by its dynamics parameters, membership parameters, and dimension. A run additionally selects a horizon, tolerance or budget, method, and seed. All rows, cells, horizon variants, tolerance variants, and derived trajectories from the same base problem belong to the same data partition.

Use separate training, validation, in distribution test, and out of distribution test manifests. Hold out some parameter ranges, peak shapes, separations, and horizons. Do not split individual cell rows randomly across training and test.

Select feature scaling, architecture, hyperparameters, thresholds, and fairness settings using training and validation data only. Freeze these choices before opening final test results.

Start with one learning pilot. The final comparison should include at least 30 distinct test instances in each claimed dimension and regime, subject to the frozen compute budget, and five independently trained model seeds. Use 20 refinement seeds for randomized baselines. These sample sizes are proposed starting points; assess precision in the pilot and record changes before the final freeze.

Training time, strong branching label generation, and preprocessing are reported separately. Calculate a break even number of repeated solves where appropriate. Training on an expensive oracle is not free.

## 8 Statistical analysis

Compare methods on paired runs of the same base problem. Report medians and paired ratios with 95 percent bootstrap intervals. Resample whole base problems, keeping their horizon and tolerance variants together, rather than treating time steps or cells as independent observations. For learned methods, retain model seed variation and use a hierarchy that resamples base problems and training seeds.

Record success rates and empirical completion curves over time. Do not remove timeouts from runtime comparisons or present a geometric mean of successful runs as an unconditional speedup.

Use at least five timed repetitions after documented JVM warmup on a stratified timing cohort fixed during the pilot, with randomized method order. Report cold process startup separately. Do not mistake these repetitions for new scientific instances. Capture JDK, garbage collector, CPU, OS, thread counts, power settings when available, and external workload.

The primary comparison is learned CAE versus the strongest deterministic adaptive baseline chosen using validation data. Other comparisons are descriptive unless a multiplicity procedure is declared before the final evaluation.

A practical improvement is a predeclared target of at least 20 percent median time reduction on the primary regime, with uncertainty supporting improvement and no certificate validity regression. This is a decision threshold, not a result or a condition for retaining negative findings.

## 9 Ablations

The required ablations remove or replace:

- Learned selection, using maximum bound and cost aware deterministic policies.
- Horizon awareness, using only the next time step.
- Local enclosures, using global Lipschitz bounds.
- Grade aware caps.
- The fairness mechanism in a stress test, with the convergence claim disabled.
- Verification, in an explicitly empirical comparison that retains its uncertified label.

Keep software and numeric settings fixed. Include cases where the verification overhead dominates and where a simple uniform grid is the fastest method.

## 10 Artifacts and reporting

Every run will store a configuration hash, protocol version, code revision or content hash, dependency locks, numeric policy, proof statement version, hardware metadata, seeds, model hash, counters, time series, result status, and output geometry.

Reports will include the complete outcome table, cost versus certificate curves, time to tolerance curves, certificate versus reference discrepancy, weak component examples, and a failure analysis.

The paper style report will present the hypothesis, closest prior work, algorithm, assumptions, proof, benchmark protocol, results, limitations, and reproduction instructions. Figures must be generated from saved artifacts. Screenshots supplement the numeric evidence.

Freeze the configuration and analysis code before the final run. Any later change creates a new version and is identified as exploratory or followed by a fresh untouched test set.
