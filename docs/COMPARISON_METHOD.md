# Deterministic comparisons and exact scalar references

The reference constructions and numerical distinctions here remain part of the implemented comparison contract. The cohort and repetition settings in this document describe the historical milestone 03 pilot. See the [learning milestone](milestones/04.md), [locked final protocol](../research/protocols/evaluation-locked-v1.json) and [analysis plan](ANALYSIS_PLAN.md) for the later 120-base, 32-method study.

**Protocol:** milestone-03-pilot-v1, 17 November 2026.  
**Stage:** exploratory pilot.  
**Scope:** local functional comparison, reference qualification, and reproducible evidence.

The executable protocol lives in research/protocols/milestone-03-pilot-v1.json. It was selected before running the pilot. It is separate from the earlier proposed final evaluation design and from future training, validation, and untouched test manifests.

## Cohort and execution

The pilot fixes 16 problems: four affine contractions, four tent maps, four logistic maps, and four coupled tent maps. Each problem has tolerances 0.2 and 0.1, 24 significant decimal digits, at most 512 completed splits, at most 513 leaves, depth 24, and FIFO fairness every fifth adaptive selection.

These deliberately modest pilot tolerances keep local runs bounded while exercising narrow peaks, weak peaks, expansion, and resource exits. They are a calibration choice, not a replacement of the final research protocol after looking at test results.

Six methods are declared for every problem and tolerance. An unavailable method remains an explicit row, giving 192 declared outcomes. Every eligible row receives one warmup repetition and five timed repetitions. A fixed random seed shuffles method order separately within each paired repetition.

The numerical work uses one worker thread. In-process timing includes the numerical solve and post-solve verification. Reference preparation, diagnostics, report IO, and cold JVM startup are excluded and labeled accordingly. Startup is not independently measured in this pilot. The environment records JDK, VM, OS, processor description, heap limit, garbage collectors, and the fact that external workload is uncontrolled.

Timing repetitions must return exactly equal mathematical payloads and verification counters. They are repeated observations of one problem, not independent scientific instances.

## Policies sharing the CAE engine

All four policies use identical bounds, precision, tolerances, budgets, bisections, and incumbent retention.

| Policy | Ranking on ordinary selections |
| --- | --- |
| Uniform | Oldest eligible leaf every time |
| Maximum bound | Largest contribution across the entire horizon |
| Next step | Largest contribution at time min(1,T) |
| Predicted gain | Heuristic predicted horizon bound reduction per estimated work |

All adaptive policies use the same periodic FIFO selection. Scores only change which leaf is selected; they do not enter any membership, trajectory, or error bound.

The predicted gain heuristic halves a cell's membership variation and spatial radii, retains its membership evaluation error and upper cap, and computes the resulting predicted horizon bound. It divides the nonnegative predicted reduction by

$$
2\bigl(3(T+1)+\operatorname{depth}(C)+1\bigr).
$$

The model counts two complete child evaluations and an explicit path reconstruction allowance. It is a deterministic ranking heuristic. In particular, its predicted child bound is not a valid certificate and need not describe the actual children.

## Exact complete alpha cuts

For a scalar tent peak of height h, width w, and center c, a positive alpha cut is empty when alpha exceeds h. Otherwise it is

$$
[c-w(1-\alpha/h),\ c+w(1-\alpha/h)]\cap[0,1].
$$

The cut of a maximum of peaks is the full union of those intervals. Components are merged only when they overlap or touch; gaps and singleton cores survive.

Under each supported continuous scalar map, each interval image is determined exactly by endpoint values and any included turning point. Affine maps have no interior turning point; tent and logistic maps turn at one half. Endpoints and parameters remain Rational values throughout.

The exact positive cut identity transports these complete unions through every time. This route uses no CAE cell recipe or decimal enclosure.

For increasing levels ending at one, define the lower staircase

$$
\mu_{\mathrm{grid},t}(x)
 =\max\bigl(\{\alpha_j:x\in[\mu_t]_{\alpha_j}\}\cup\{0\}\bigr).
$$

If delta is the largest consecutive level gap, including the gap from zero, then

$$
0\le\mu_t(x)-\mu_{\mathrm{grid},t}(x)\le\delta,
\qquad
d_E(\mu_t,\mu_{\mathrm{grid},t})\le\delta.
$$

The alpha grid baseline uses a uniform grid with ceil(1/tolerance) levels. Its bound therefore meets the requested tolerance on the exact supported scalar models. Exact rational work is recorded separately from CAE decimal interval calls. This is a different representation with different costs, not merely another CAE selector.

## Exact PWL transport specialization

The published transport baseline specializes Section 4.1 of [Kupka and Skorupova (2021)](https://www.mdpi.com/2227-7390/9/21/2737). Source linear pieces are split at map knots, transported through each linear branch, and combined by pointwise maximum.

Our implementation uses exact fractions and retains overlapping pieces implicitly. It explicitly handles reversed branches, constant images, singleton spikes, and jumps at support boundaries. The full zero baseline is shared.

The eligible dynamics are exact affine and tent maps. The nonlinear PSO preprocessing and the authors' reported timings are not reproduced. Logistic and coupled maps are marked unavailable for this specialization. Tests check the paper's listed Example 6 and parity against a separate exact alpha cut construction.

The output is an exact representation under these restricted assumptions, so a complete trajectory has error bound zero. An output piece budget can stop a run; an incomplete transition is discarded and no full horizon zero bound is reported. This restricted result does not imply that an exact practical representation exists for every model in the research plan.

## Independent continuous error brackets

Four scalar problems are designated for a denser 64 level reference before running the pilot. For a finite CAE state, the distance to that entire continuous staircase is computed exactly.

The staircase endograph is a finite union of interval rectangles I times [0,alpha] and the common zero baseline. For the directed distance from a rectangle's top to finite atoms, activate target atoms in descending grade order. At each activation threshold v_i = max(h-g_i,0), where h is the source height and g_i is the activated atom's grade, the entire source interval union is covered within

$$
\max\{v,\ d_{\rightarrow}(I,\{\text{active locations}\})\}.
$$

Taking the smallest such radius, also capped by alpha through the common baseline, gives the exact directed distance. The reverse direction minimizes each finite atom's distance to every rectangle and the baseline.

For exact interval union distances, possible interior maxima occur at the midpoints of gaps in the target union. Endpoints alone would miss them. The tests include this negative case and compare the staircase metric with an independent finite endograph metric on exact grid-valued states.

Let d be the exact finite-to-staircase distance and deltaRef the reference's vertical error. The true continuous error lies in

$$
[\max(0,d-\delta_{\mathrm{ref}}),\
 \min(1,d+\delta_{\mathrm{ref}})].
$$

These observations carry VERIFIED_ENCLOSURE quality, with both the reference distance and its error saved separately. A verified lower endpoint exceeding a CAE certificate is a correctness failure and stops reporting. An upper endpoint exceeding it is inconclusive, since the bracket can be conservative.

No joint two dimensional continuous discrepancy is invented. Those reference fields are explicitly unavailable.

## Positive cut diagnostics

The diagnostic levels are fixed in the protocol, including 1/200 and 1/100 for weak components. Exact full reference cuts are compared with the complete approximation cuts.

Ordinary Hausdorff distance is recorded only when both cuts are nonempty. BOTH_EMPTY, REFERENCE_EMPTY, and APPROXIMATION_EMPTY have an absent distance, not a fabricated zero. A small endograph certificate can coexist with a missing weak cut; the report preserves that discrepancy.

Reference interval counts and approximation element counts describe their actual representations. An isolated finite representative is not declared a topological component under an unspecified raster.

## Evidence and replay

Run after the checks complete:

~~~powershell
.\gradlew.bat comparisonDemo
.\gradlew.bat verifyComparison
~~~

The comparison task depends on checks and waits for foundation demos if invoked together. For cleaner timing conditions, invoke it separately after the build.

Output lives under artifacts/comparison:

~~~text
manifest.json
summary.md
outcomes.csv
measurements.csv
alpha-diagnostics.csv
reference-errors.csv
refinement-curves.csv
runs/*.json
~~~

The manifest contains the embedded protocol, its canonical typed JSON hash, the source and build input hash, complete outcome rows, timing observations, and numerical file hashes. Per run files hold full typed geometry and refinement histories. Exact decimals and fractions are JSON strings.

Verification reloads existing files, checks the complete paired cohort and seeded method order, reexecutes numerical results, recomputes diagnostics, and compares counters and metrics. It does not replace timing observations or write the input bundle. Safe relative filenames keep numerical files inside the selected report directory.

The hashes provide consistency and provenance, not authentication. Recorded elapsed times cannot be independently reconstructed from a numerical replay. These local measurements do not establish a final winner, a universal speedup, scientific novelty, or independent mathematical review.
