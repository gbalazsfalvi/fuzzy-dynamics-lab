# Independent internal mathematical and source review

**Review date:** 17 November 2026.  
**Review type:** independent-pass internal AI/subagent mathematical review. This is a second-pass model review, not external expert peer review, formal verification, or evidence that a different underlying model independently confirmed the results.  
**Disposition:** no counterexample found to P1, P2, P3, or the scalar staircase metric under their stated assumptions. The closest-literature full-text comparison remains open. The implementing agent corrected a public reference-container API's conditional certification language during this review, and I inspected that correction.

## Scope and limits

I reviewed `AGENTS.md`, `PROOF_OBLIGATIONS.md`, `CAE_METHOD.md`, `COMPARISON_METHOD.md`, `RESEARCH_REVIEW.md`, and `research/source-register.json`. I inspected the current Java sources `AlphaGridEndograph`, `IntervalUnion`, `AlphaCutTrajectory`, `ExactAlphaCuts`, and `AlphaGridEndographTest`. I independently derived the staircase threshold formula and executed an exact-rational comparison against a separate geometric oracle, described below.

This review does not certify the entire Java implementation, all interval primitives, all tests, the learned selector, benchmark execution, statistical analysis, publication workflow, or workbench. I did not run the Gradle suite or independently reproduce the reported comparison artifacts. Previously recorded full-text reviews of the 2001, 2011, 2021, and 2022 papers were read as project records; they were not repeated in full during this pass. The two missing closest papers were searched again through public sources.

## P1: endograph certificate

The common universe must remain the compact unit box, with the infinity spatial norm and maximum product metric specified in the proof note. Upper semicontinuity of the initial membership and continuity of the iterate imply that each nonempty fiber is compact and its membership maximum is attained. For every positive alpha, the transported cut is the continuous image of a compact initial cut. It is compact, including when empty; this also establishes upper semicontinuity of the transported membership. Thus the endographs used for Hausdorff distance are compact. Nonnormality and an identically zero membership cause no exception.

Write `v_C = kappa_C + sigma_C` and `q_C,t = max(rho_C,t, v_C)`. Both directed estimates in the proof are valid:

\[
D(E(\mu_t),E(\widehat\mu_t))
\le \max_C\min\{M_C,q_{C,t}\},
\]

\[
D(E(\widehat\mu_t),E(\mu_t))
\le \max_C\min\{a_C,\max(\rho_{C,t},\sigma_C)\}.
\]

In the first direction, a positive-height true point has a preimage in some covered cell; its stored representative supplies the spatial and vertical match. In the second direction, a maximum-height colliding atom comes from at least one cell because the set of representatives is finite. That cell's exact representative trajectory supplies the reverse match. Both directions can instead use the point at height zero at the same spatial location. Taking the larger directed estimate is bounded by the stated `max_C min(max(M_C,a_C), q_C,t)`.

This argument explains why the maximum must include `a_C` in the grade cap when grades can overestimate the true membership. Replacing it by `M_C` is invalid in the general P1 contract: for an identically zero true state, a stored atom of height one, `sigma_C=1`, `M_C=0`, and any valid spatial radius, the true distance is one. A cap of zero would be false. The shipped lower-grade recipe avoids that particular overestimate, but P1 explicitly permits certified two-sided evaluation errors.

Boundary overlap, noninjective maps, colliding representatives, and missed height-one peaks do not invalidate the proof. Replacing a collision maximum by a sum would invalidate it. Replacing the complete zero baseline with the transported representative baseline would change the mathematical object and invalidate the grade-cap argument.

The membership-range formula in `CAE_METHOD.md` is sufficient: if the exact representative value belongs to `[p_C,q_C]` and every cell value belongs to `[l_C,u_C]`, its absolute variation is bounded by `max(u_C-p_C,q_C-l_C)`. The independent Lipschitz alternative is also valid, so the minimum of these two certified upper bounds is valid. Adding the separately certified grade-evaluation error is conservative but safe.

**Conclusion:** P1 is a valid covering argument under the declared assumptions. This review establishes no novelty for it or for either sharper directed estimate above.

## P2: spatial propagation

For each initial point `x` in a cell,

\[
\|f^{k+1}(x)-y_{C,k+1}\|_\infty
\le L_{C,k}\|f^k(x)-y_{C,k}\|_\infty+\eta_{C,k}.
\]

The hypotheses require the Lipschitz estimate to include both `f^k(x)` and `y_C,k`. The convex hull of the verified reachable box and representative meets that requirement when the Jacobian bound is valid on the hull. A representative outside a tightened reachable box must still be included. Intersecting two independently valid image enclosures is valid; a disjoint intersection signals an error.

A concrete negative control explains why representative-only derivatives are insufficient. For `f(x)=x^2`, cell `[0,1]`, and representative zero, `f'(0)=0` would predict zero next radius from initial radius one and zero residual. Yet `f(1)=1`. This is a counterexample to the weakened derivative rule, not to P2; the declared whole-region rule excludes it.

All recurrence products, sums, direct-image bounds, and residuals must be outward bounds. Merely calling a number a radius does not establish that property. This pass reviewed the method contract, not every decimal arithmetic operation.

## P3: finite-horizon convergence and policy independence

The finite-horizon inequality follows from exact representative arithmetic, `rho_C,t <= L^t r`, and `kappa_C <= K r`:

\[
\max_{0\le t\le T}d_E(\mu_t,\widehat\mu_t)
\le \max\{1,L^T,K\}\,r.
\]

The factor one correctly handles time zero and `L<1`. For `T=0`, the intended factor is one, including a constant map with `L=0`; it is preferable to read the temporal factor as `max_{0<=t<=T} L^t` with `L^0=1` rather than leave `0^0` ambiguous.

Periodic FIFO selection eventually splits any fixed leaf in an unlimited run: only finitely many leaves precede it when it is created, newer children cannot precede it, and each FIFO event removes an oldest remaining leaf. Other selections can remove older leaves sooner. Longest-side bisection then reduces the maximum diameter. Starting from the unit box in fixed dimension, after every block of `d` bisections along a branch every coordinate has been halved at least once. For any target diameter there is a finite depth that suffices, and only finitely many ancestors below that depth. Fairness eventually processes all those ancestors.

This proof requires fairness among all active leaves and no permanently ineligible leaves caused by depth limits. The actual finite-budget solver appropriately promises only a retained complete certificate and a status. A learned or heuristic selector changes this argument only if it defeats the guaranteed fairness events or corrupts the complete cover. It cannot justify convergence from a bounded run.

Global membership regularity is essential for this convergence claim. For example, take identity dynamics and membership one at an irrational point and zero elsewhere. This membership is upper semicontinuous, so P1 can apply, but dyadic midpoint representatives never hit the peak. Every finite midpoint approximation is the zero state, and its endograph distance from the exact state is one. Refining the partition does not help. This does not contradict P3 because the membership is not Lipschitz. It demonstrates why P1's weaker assumptions must not be reused as P3's convergence assumptions.

Fixed nonvanishing numerical errors likewise do not prove arbitrary-tolerance convergence. The paper and portfolio must preserve the exact-or-vanishing-error, fixed-horizon qualification. The proposed method does not establish a uniform-in-time result or an efficiency theorem.

## Exact alpha staircase and endograph metric

Let `0=alpha_0 < alpha_1 < ... < alpha_m=1` and let `A_j` be the exact positive cuts of a membership `u`. The lower staircase `s(x)=max({alpha_j:x in A_j} union {0})` satisfies `0 <= u(x)-s(x) <= delta`, where `delta=max_j(alpha_j-alpha_{j-1})`. When `u(x)` lies between two levels, its lower neighboring level gives the result; below the first level the zero baseline does. Membership one is included because the grid ends at one. Therefore `E(s)` is contained in `E(u)` and every point of `E(u)` has a vertical match within `delta`. The endograph error is at most `delta`.

For the supported scalar maps, endpoint and included critical-point evaluation gives the complete image of each compact interval. The continuous image is an interval; the exact extrema fix its endpoints. Taking the union preserves disconnected cuts. Iterating this operation propagates positive cuts exactly. This is not a valid joint two-dimensional geometry algorithm.

Here is a complete derivation of the less obvious forward staircase metric. Fix a nonempty source cut union `A` at height `h`, and target atoms `(p_i,g_i)`. Put

\[
v_i=(h-g_i)_+,\qquad
F_h(x)=\min\left\{h,\min_i\max\{|x-p_i|,v_i\}\right\}.
\]

The baseline contributes `h`; the other terms are exact distances to the target vertical segments. Distance to the target endograph is nondecreasing with source height, so only the source rectangle's top needs checking. Sort atoms by decreasing grade, hence increasing `v_i`, and let `P_k={p_1,...,p_k}`. Then

\[
\sup_{x\in A}F_h(x)
=\min\left\{h,\min_k\max\{v_k,D(A,P_k)\}\right\}.
\]

For the upper inequality, any prefix covers all points of `A` within the spatial directed distance and has vertical deficits at most `v_k`. For the lower inequality, let `R=sup_A F_h`. If `R=h`, the baseline already supplies equality. If `R<h`, every point must have a target atom with vertical deficit at most `R` and spatial distance at most `R`. Take the full prefix of atoms with `v_i<=R`. Its spatial directed distance is at most `R`, and its last vertical deficit is at most `R`, so a candidate on the right is at most `R`. Tied grades cause no problem: the full tied prefix is among those scanned. Empty target atoms give distance `h` through the baseline.

This threshold argument avoids an unjustified interchange of a supremum and a minimum. The documentation should define its activation threshold explicitly as `(h-g_i)_+`, not as the grade itself. `AlphaGridEndograph` uses the correct quantity.

For the reverse direction, each atom's top has distance

\[
\min\left\{g_i,\min_{j:A_j\ne\varnothing}
\max\{\operatorname{dist}(p_i,A_j),(g_i-\alpha_j)_+\}\right\}.
\]

Maximizing over atom tops and all source rectangles gives the symmetric Hausdorff distance. These formulas agree with the inspected implementation.

For a closed interval union target, distance is zero on its components, affine outside its hull, and a tent on each interior gap. Consequently the only needed candidates for a directed maximum over source intervals are their endpoints and target-gap midpoints lying in the source. `IntervalUnion.directedTo` checks exactly those candidates. The example `A=[0,1]`, target locations `{0,1}` has directed distance `1/2`, although both source endpoints have distance zero. Endpoint-only checking would fail.

A mixed-grade example additionally exercises the threshold logic: take source rectangle `[0,1] x [0,1]`, target atom `(0,1)` and target atom `(1,1/2)`. The first prefix gives radius one; the complete prefix gives `max(1/2,1/2)=1/2`. The true forward distance is `1/2`, and the reverse distance is zero. Choosing just one atom globally would incorrectly give radius one.

Finally, if `d=d_E(s,a)` and `d_E(u,s)<=delta`, the triangle and reverse triangle inequalities give `[max(0,d-delta), min(1,d+delta)]`. A bracket upper endpoint exceeding a CAE certificate is not a contradiction. A rigorously valid lower endpoint exceeding it is.

### Executed independent finite check

I ran an in-memory Python `fractions.Fraction` check with seed `17112026`. It generated 1,000 cases with one to four closed source intervals, zero to seven target atoms, coordinates on the sixteenth grid, positive rectangle heights on the eighth grid, and target grades on the eighth grid. Overlaps, singleton intervals, zero grades, duplicate coordinates, and empty targets were allowed. No output artifact or source file was created.

The comparator was an independent affine-arrangement oracle. For each atom it included the lines `x-p_i`, `p_i-x`, and `(h-g_i)_+`, together with the baseline constant `h`. It evaluated `F_h` at source endpoints and every pairwise line intersection inside the source. Between consecutive intersections, the ordering of all lines is fixed, so the nested minima and maxima select an affine function and attain their interval maximum at an endpoint. This computes the continuous directed maximum exactly, not on a raster. Its results equaled the prefix-threshold calculation in **1,000 of 1,000 cases**. The mixed-grade example returned exactly `1/2`.

This finite check supplements the derivation. It is not a Java execution test, a continuous proof by itself, or a full independent replay of the pilot.

## Required qualifications and fixes

1. **Keep the full-text novelty gate open.** Do not describe P1-P3, endograph approximation, adaptive decomposition, or maximum-based reconstruction as established novel results. The access findings below do not satisfy the original closest-literature comparison exit condition.
2. **Qualify `AlphaCutTrajectory.errorBound()` at the public construction boundary.** The constructor validates level order and list shape but does not establish that supplied cuts are exact cuts of a particular problem. Its largest-level-gap value is a certified true-reference error only under that additional premise. For a concrete counterexample, supply the eight uniform levels and empty cuts at every level while claiming the true membership is identically one. The constructor accepts the shape; its gap is `1/8`; the represented zero staircase has actual error one from the claimed true state. The generated `compute(problem,levels)` path is not implicated. Clarify the public Javadoc and any imported-artifact trust boundary; consider distinguishing an unverified staircase from a problem-verified reference. Nesting validation can reject inconsistent cuts, but cannot itself establish their exactness relative to a problem. Do not imply it can.
3. **Define the activation threshold in the method text.** State `v_i=max(0,h-g_i)` and include the coverage argument above. This is a documentation precision fix, not a discovered numerical error.
4. **Keep review labels accurate.** This report permits “independent internal mathematical review” with its AI/subagent qualification. It does not permit “externally peer reviewed,” “formally verified,” or “independently established novel theorem.”

**Resolution of item 2:** after the finding was reported, the implementing agent updated the class and `errorBound()` Javadoc. I re-read the changed source: it now explicitly conditions the bound on exact-cut provenance, states that the public constructor validates shape rather than provenance, and distinguishes the `compute` factory from arbitrary caller-supplied data. This resolves the identified documentation overstatement; it adds no untrusted-input proof checking and changes no numerical behavior. The counterexample remains useful as a trust-boundary example.

These are the findings and follow-up resolution at the inspected snapshot. The implementing agent may resolve the threshold-text clarification separately; this reviewer edited only this report.

## Lawful public full-text access recheck

The search covered exact-title and DOI queries, publisher records, university author/publication records, and live OpenAlex locations on the review date. No purchase, author contact, authentication bypass, or restricted-content workaround was used. No full article for either target DOI was obtained. This is a record of the routes checked, not proof that no lawful copy exists anywhere.

| Target | Public evidence inspected in this pass | Full-text status |
| --- | --- | --- |
| Chalco-Cano et al., 2007, DOI `10.1016/j.ins.2007.07.007` | Indexed [publisher preview](https://www.sciencedirect.com/science/article/pii/S0020025507003623), [Universidad de Tarapaca record](https://investigadores.uta.cl/en/publications/the-extension-principle-and-a-decomposition-of-fuzzy-sets-2/), live [OpenAlex record W2020726968](https://api.openalex.org/works/W2020726968) | Publisher fetch returned HTTP 403. University record offers metadata, abstract, DOI and Scopus links. OpenAlex reported `is_oa=false`, `oa_status=closed`, `oa_url=null`, `best_oa_location=null`, `any_repository_has_fulltext=false`, and no PDF URL. **Not reviewed in full.** |
| Kupka, 2016, DOI `10.1016/j.fss.2015.03.014` | Indexed [publisher preview](https://www.sciencedirect.com/science/article/abs/pii/S0165011415001505), [official author page](https://ifm.osu.cz/jiri-kupka/32760/), linked [university publication record 58440](https://portal.osu.cz/wps/portal/is/publsearch?record_id=58440), live [OpenAlex record W2021862876](https://api.openalex.org/works/W2021862876) | Publisher fetch returned HTTP 403. University record exposes citation and abstract, not the complete article. OpenAlex returned the same closed/no-full-text fields as above. **Not reviewed in full.** |

The 2007 preview and university abstract already describe decomposition, local function approximation, and approximation bounds. The 2016 preview and official university record already establish that approximation of continuous-map Zadeh extensions and metric choice, including the endograph metric, are prior subjects. These facts rule out broad “first endograph approximation” or “first decomposition with error bounds” language. They do not establish the detailed theorem assumptions, metric product conventions, algorithm steps, constants, or equivalence to CAE. Those comparisons remain unresolved.

OpenAlex is used here only as a locator/access registry; the mathematical descriptions above derive from the publisher and university material. An empty open-access location is not a mathematical source and does not prove absence of prior art.

## Defensible narrow contribution statement

The project develops an auditable implementation of initial-domain adaptive refinement for a declared family of discrete fuzzy dynamics. Its representation combines transported finite representatives, whole-cell membership bounds, directed trajectory enclosures, complete-cover verification, and finite-horizon stopping certificates. It separates policy selection from the validity of numerical bounds, preserves nonconvex scalar cut unions, and supplies exact scalar reference geometry for reproducible comparisons.

This is a concrete engineering and experimental contribution to investigate, not an established new mathematical principle. P1-P3 should currently be presented as internally derived specializations of covering and Lipschitz arguments whose exact relationship to the closest approximation literature has not been resolved. Any learned-policy efficiency claim requires the actual held-out protocol and measurements; this review supplies none. Public source code, a rigorous report of limitations, and a portfolio article can be released with these narrow claims, while explicitly retaining the open full-text and external-review limitations.
