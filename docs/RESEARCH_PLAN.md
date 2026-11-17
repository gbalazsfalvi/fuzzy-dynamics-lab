# Fuzzy Dynamics Lab research plan

This is the original design record, including options and future-tense language from planning. The [README](../README.md), delivered milestone notes, [analysis plan](ANALYSIS_PLAN.md) and [locked final protocol](../research/protocols/evaluation-locked-v1.json) describe the actual release scope. In particular, the published PWL comparison is a qualified exact specialization; the broader PSO and closest-full-text research options below are not presented as completed reproductions.

**Plan date:** 17 November 2026.  
**Research target:** an original, carefully compared method for a declared class of fuzzy dynamical simulations, supported by a proof of its numerical certificate.  
**Working method name:** Certified Adaptive Extension, or CAE.

The project will investigate whether adapting a partition of the initial state space over an entire finite horizon reduces the cost of approximating a fuzzy trajectory. The learned component will rank refinements. It will not supply the mathematical error guarantee.

## 1 Research contribution

The proposed contribution is a specific combination of finite fuzzy representatives, certified trajectory enclosures, membership variation bounds, horizon aware refinement, and an evaluated learning policy. The study must identify which parts are inherited from established approximation and reachability methods and which parts add a demonstrable result.

The main geometric bound is a tractable proof target, rather than a promise to solve a long standing open problem. A proof written for this project in 2026 can be an original derivation without being a new theorem in the mathematical literature. Novelty must be established separately.

The initial research questions are:

| ID | Question | Evidence required |
| --- | --- | --- |
| RQ1 | Does CAE provide valid and informative endograph error bounds across a finite horizon? | Mathematical argument, independently reviewed assumptions, verified arithmetic, and reference checks |
| RQ2 | Does horizon aware adaptive refinement outperform uniform refinement and local greedy refinement at matched tolerances and budgets? | Paired benchmark comparisons, including verification overhead and failures |
| RQ3 | Does learned refinement improve on the strongest deterministic adaptive policy? | Held out problem instances, ablations, inference overhead, and confidence intervals |
| RQ4 | When does a small endograph error conceal a meaningful low membership component? | Controlled weak component examples and declared alpha cut diagnostics |

The core deliverables are an algorithm specification, a mathematical note, an experiment protocol, implementations of relevant baselines, a reproducible result bundle, and a workbench for examining those results.

## 2 Foundation and novelty audit

Kloeden's [1982 paper](https://doi.org/10.1016/0165-0114(82)90056-2) supplies an early mathematical foundation. Kupka's [2016 paper](https://doi.org/10.1016/j.fss.2015.03.014) studies approximation of the extension principle and the effect of choosing different metrics. Kupka and Škorupová's [2021 study](https://www.mdpi.com/2227-7390/9/21/2737) provides a one dimensional PSO based simulation algorithm.

Before fixing the originality claim, compare CAE with the [2007 decomposition and multilinearization method](https://doi.org/10.1016/j.ins.2007.07.007), the 2016 procedure, the 2021 simulation method, and the [2022 automatic piecewise linearization study](https://www.mdpi.com/2227-7390/10/5/808).

Learning a branching policy is already established in adjacent verification research, including [Lu and Kumar's work](https://arxiv.org/abs/1912.01329). CAE must therefore be evaluated as a particular fuzzy approximation method, not advertised as the invention of using learning to guide certified computation.

The novelty audit will record the nearest theorem, algorithm, assumptions, implementation, and evaluation for each proposed contribution. It is an entry condition for an originality claim, not a reason to postpone reproducible implementation indefinitely.

## 3 Mathematical scope

The first supported class is:

- A compact normalized state domain X = [0, 1]^d, where d is one or two.
- A continuous self map f from X to X, with certified global or local Lipschitz bounds or an interval enclosure procedure.
- An initial membership function mu0 from X to [0, 1] with computable variation bounds on every cell.
- A finite integer horizon T.
- The max product endograph metric defined in the mathematical note.

Initial functions may have several separated peaks and nonconvex alpha cuts. The first convergence result assumes a Lipschitz initial membership function. Upper semicontinuity alone is sufficient for some representation arguments but does not supply a computable numerical certificate.

The initial implementation will use named built in maps and membership families. A user supplied black box without verified bounds can run only in an explicitly empirical mode.

## 4 Proposed algorithm

### 4.1 Initial partition and representatives

Cover X with finitely many cells. A cell stores its geometry, a representative point, a membership grade and its evaluation error, an upper membership bound, and a bound on membership variation relative to its representative.

For every representative, compute its trajectory through the requested horizon. Alongside it, propagate a verified enclosure of the trajectories that start anywhere in that cell.

The approximation at each time is a finite fuzzy set at the representative trajectory locations. Coincident locations are merged with maximum membership. The initial membership grades remain attached to their trajectories; no intermediate rasterization or forced normalization is performed.

This representation deliberately separates the spatial trajectory error from the initial vertical membership error.

### 4.2 Cell certificates

For each cell and time, calculate a radius that contains every exact trajectory from that cell around the approximate representative trajectory. Combine that radius with the initial membership variation and grade evaluation error.

The mathematical note gives an internal derivation of an endograph bound. A grade aware cap can reduce a cell's contribution when its entire membership profile is low. The workbench must show when this permits a weak component to be ignored at the chosen tolerance.

Intervals or analytic bounds establish the certificate. Residuals measured only on a sample grid do not establish it.

### 4.3 Refinement selection

Implement selection strategies through the same interface:

1. Uniform refinement using a FIFO queue.
2. Deterministic selection by the largest current contribution to the horizon certificate.
3. Deterministic selection by predicted reduction per unit of computational cost, using nonlearned features.
4. A learned ranking of candidate cell splits.

All adaptive strategies use a fairness mechanism. Initially, every fifth selection uses the oldest active cell; the other four use the strategy. This gives the convergence argument a precise condition and makes the safety mechanism comparable across policies.

Refinement uses dyadic bisection. In two dimensions, split the longest side, rotating tie breaks so cell diameters shrink. A learned choice of split dimension is a later ablation, not a prerequisite.

A refinement can worsen the certificate and even the actual endograph error, because replacing midpoint representatives changes the approximation. Preserve the best completed approximation snapshot, identified by its certificate and full provenance. The implemented [method specification](CAE_METHOD.md) gives an exact two peak counterexample. Do not claim that every individual split monotonically improves the bound.

### 4.4 Stopping and results

A tolerance run succeeds only when the certificate is within tolerance at every requested time. A budget run returns the best available certificate, including any unresolved times.

The current result statuses are CERTIFIED, BUDGET_EXHAUSTED, NUMERICAL_FAILURE, and CANCELLED. Unsupported assumptions are rejected at the typed model boundary; a structured UNSUPPORTED_ASSUMPTIONS API response is a later integration concern. The grade capped bound is at most one. The tolerance is strictly below one so the trivial bound cannot pass as an accuracy success.

A run stores the complete active partition and the retained best snapshot so that reported results can be inspected independently of the UI.

## 5 Proof programme

| ID | Statement or task | Expected status |
| --- | --- | --- |
| P0 | Define the endograph metric, the common zero baseline, and the exact fuzzy trajectory | Definition and established background |
| P1 | Bound the endograph distance using cell trajectory radii and membership variation | Internal proof argument supplied; mathematical review required |
| P2 | Derive trajectory radius propagation from a valid local Lipschitz or interval enclosure | Standard numerical reasoning specialized to the representation |
| P3 | Show that any refinement schedule preserves validity, and a fair schedule converges for a fixed finite horizon under the stated regularity assumptions | Proof target with argument supplied |
| P4 | Compare the statements and constants against the closest literature | Required originality audit |
| P5 | Investigate a sharper computable bound or a restricted efficiency result if P1 to P3 are already covered | Conditional extension, driven by findings |

The initial theory milestone succeeds when another reader can check every definition, assumption, and inference. The novelty milestone succeeds only when a precise difference from the closest theorem or method can be defended.

A performance hypothesis about learning is not converted into a theorem by numerical tests. If no useful new general theorem emerges, the project may still produce a strong experimental method and a correct, attributed mathematical analysis.

## 6 Benchmark families

Start with affine contractions as analytic checks. Add tent maps to test folding, logistic maps to test nonlinear stretching, and a two dimensional coupled tent map to test separated regions with a tractable compact domain.

Use single peaks, multiple separated peaks, narrow peaks, and a weak distant peak. Parameterize membership widths, heights, and separations so that training and test instances differ.

Do not claim physical validation from these synthetic maps. A mechanical or control application can follow after a separate model and enclosure validation.

The detailed budgets, horizons, metrics, and reference hierarchy are in the [experiment protocol](EXPERIMENT_PROTOCOL.md).

## 7 Learning design

Begin with a simple learned score, such as a small multilayer perceptron, and compare it with an interpretable regression model. Reinforcement learning and graph networks are optional later choices.

Features may include cell width, membership variation, upper membership grade, trajectory radius over time, local expansion bounds, split depth, estimated evaluation cost, and remaining tolerance. Feature normalization is fitted on training data only.

Training labels can be obtained from bounded strong branching: tentatively evaluate several candidate splits on training cases and measure the reduction in the certificate per unit of cost. The label generation cost is part of the training budget.

Freeze the model before evaluating held out cases. Use the same Java numerical engine for deterministic and learned policies, with a parity check for any exported model. Python remains responsible for training and analysis. Select a Java inference format at the learning milestone and include its latency.

A learned dynamics surrogate is outside the first method. Adding it later requires a bound on its residual over whole cells, not merely validation points.

## 8 Software architecture

The numerical engine is the primary product. The API and UI use its public application interface.

~~~text
fuzzy-dynamics-lab/
  numerics-core/        Mathematical objects, maps, bounds, refinement, certificates
  experiments/         Java CLI, baseline adapters, result manifests, counters
  api/                 Spring Boot endpoints, local job lifecycle, artifact access
  web/                 React and TypeScript experiment workbench
  learning/            Python datasets, training, evaluation, model export
  research/
    proofs/            Reviewed proof versions and claim ledger
    protocols/         Frozen experiment configurations and changes
  docs/                Method, related work, experiment and reproduction instructions
  artifacts/           Generated local runs, figures, tables, and reports
~~~

This is the planned complete layout. Milestones 01 and 02 implement numerics-core and experiments, including the deterministic adaptive engine and evidence replay. The API, workbench, learning tools, and comparative benchmark suite remain future work. Source access and delivery status are recorded in [Milestones 00 and 01](milestones/00-01.md) and [Milestone 02](milestones/02.md).

Use Java 21, a Gradle wrapper, a selected supported Spring Boot release, React with TypeScript, and a pinned Python environment. Lock exact dependencies when bootstrapping. The [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [React TypeScript documentation](https://react.dev/learn/typescript) are the compatibility references.

The initial verified arithmetic path will use directed BigDecimal interval operations for the built in rational, polynomial, and piecewise linear models. Precision is a recorded configuration parameter, selected on pilot data. An exact rational implementation supplies small test oracles. A faster binary floating point path may be added, but it is empirical unless its outward bounds and model parameter conversion are verified. Stored grades can use a certified lower endpoint with the interval width recorded as their error.

The Java core has no Spring, HTTP, database, or Python dependencies. Bound computations expose their assumptions and numeric provenance. A certificate verifier must use recomputed interval or analytic checks rather than trusting a caller supplied bound.

Use immutable value objects where practical. Document equations, units, precision, assumptions, and failure conditions with Javadoc. Comments explain numerical and design decisions. Keep experimental policies replaceable without duplicating the extension engine.

Initial persistence is a local directory containing JSON manifests and CSV data. Additional infrastructure requires a demonstrated research need.

## 9 Workbench

The workbench will let a reader select a benchmark, membership profile, horizon, tolerance, method, and budget. It will show:

- The initial state and the approximation at a selected time.
- Representative points and certified trajectory enclosures as separate layers.
- Refinement activity and the reason a cell was selected.
- The error certificate, reference discrepancy, cost, and runtime.
- Alpha cut diagnostics for weak or separated components.
- A method comparison and a replayable artifact manifest.

Use a restrained scientific interface with clear legends and accessible chart colors. A smooth display interpolation must not be presented as the certified mathematical representation.

Backend and frontend remain local. Headless experiments must not require an open browser.

## 10 Milestones and exit criteria

| Milestone | Main work | Required exit evidence |
| --- | --- | --- |
| 00 Research contract | Obtain the full key papers, complete the nearest work matrix, freeze definitions and contribution statement | Source access record, claim ledger, benchmark eligibility rules |
| 01 Numerical foundation | Implement mathematical types, exact small references, interval primitives, and basic maps | Analytic and rational checks, interval containment tests, documented rounding policy |
| 02 Certified method | Implement cell trajectories, certificates, deterministic refinement, and retained snapshots | P1 to P3 review, certificate replay, failure injection, deterministic results |
| 03 Baseline comparison | Implement uniform, alpha cut, greedy and relevant published method comparisons | Protocol version locked, parity checks, pilot costs, reference error qualification |
| 04 Learned refinement | Generate training cases, train and freeze ranking models, add inference adapter | Split manifest, model hash, train cost, parity checks, ablations |
| 05 Research evaluation | Execute the frozen comparison and investigate failures | Raw runs, complete outcome table, intervals, plots, claim specific conclusions |
| 06 Workbench and local demo | Add Spring Boot API, React UI, local orchestration, screenshot scenarios | Headless reproduction plus UI replay of the same saved experiment |
| 07 Research report | Complete the mathematical note, method description, limitations, and bilingual portfolio draft | Independently checked claims, figure provenance, clean local publication candidate |

Milestones 00 and 01 precede the main implementation. The workbench follows a trustworthy numerical engine and pilot comparisons. Baseline prototyping can inform the method, but final test results are produced only after the protocol is frozen.

**Current release:** milestones 01 through 07 are implemented: numerical foundation, CAE, comparison methods, frozen learned models and evaluation, the local workbench and study/presentation assets. See [05](milestones/05.md) for measured findings and [07](milestones/07.md) for publication surfaces. The 00 closest-full-text exit gate remains open. P1 to P3 have internal review and exact reference evidence; external mathematical review and scientific originality remain unestablished.

Planning allowance: roughly 25 to 45 focused engineering days for a complete research prototype, with additional uncertainty for full text access, mathematical review, and a publishable novelty result. This is an effort estimate, not a promised publication date.

The first 2026 mathematical draft is targeted during milestones 00 to 02. Use the actual completion date and review status on each proof version.

## 11 Quality and reproducibility

Use tests that can reveal numerical errors: analytic reference solutions, exact rational finite examples, interval containment, max preservation, positive alpha cut identities, adversarial peaks, collisions, and invalid assumptions.

Property tests and sampled containment checks supplement the proof. They do not replace it. Keep empirical and verified execution modes separate in artifacts and UI.

Record source revision when available, an implementation content hash when not, dependency locks, configuration hash, numeric mode, hardware, thread counts, seeds, model hash, counters, runtime, memory, and reference quality. Save timeout, memory, and certificate failures.

Working verification and demonstration commands are in [README](../README.md). The milestone 03 pilot is exploratory history; the later locked protocol and analysis plan define the actual final comparison. Documentation must not show nonexistent commands as working instructions.

## 12 Completion criteria

A research release requires:

1. An executable method with explicitly supported assumptions and a verified arithmetic path.
2. A checked mathematical argument connected to implementation invariants.
3. A comparison with the strongest applicable deterministic baselines.
4. A held out evaluation of learning, including cases where it loses.
5. A conclusion supported by raw data and repeatable analysis.
6. A local demo, figures, screenshots, and English documentation.

A measured negative result is acceptable. A claim of better performance requires a practically meaningful effect and uncertainty analysis. A claim of new mathematics additionally requires a defended novelty argument and expert review.

Publication uses distinct source, study/data and bilingual static-portfolio surfaces. The numerical API and workbench remain local loopback software; static publication does not expose that service.
