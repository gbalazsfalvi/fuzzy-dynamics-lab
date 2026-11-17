# Mathematical specification and proof obligations

**Draft date:** 17 November 2026.  
**Status:** internal derivation for a proposed algorithm. The arguments below require mathematical review and comparison with the nearest published results before any claim of scientific novelty.

The proposed certificate concerns fuzzy membership transported by a discrete deterministic map. It does not establish a performance advantage for learning, prove correctness of an implementation, or describe all classes of fuzzy dynamical systems.

## 1 Definitions

Let X = [0, 1]^d with the infinity norm, where d is one or two in the initial implementation. Let f map X continuously into X. Let mu0 map X into [0, 1] and be upper semicontinuous.

For integer t at least zero, define the exact state by the Zadeh extension of the t fold map:

$$
\mu_t(y)=
\begin{cases}
\sup\{\mu_0(x): f^t(x)=y\}, & (f^t)^{-1}(y)\ne\varnothing,\\
0, & \text{otherwise}.
\end{cases}
$$

The identity between this expression and repeated application of the extension principle follows by composition of the suprema. Compactness and upper semicontinuity ensure that a nonempty fiber attains the relevant maximum.

For any membership function u, define its endograph on the common universe:

$$
E(u)=\{(x,a)\in X\times[0,1]:0\le a\le u(x)\}.
$$

Equip the product with the max metric:

$$
d_\Box((x,a),(y,b))
 =\max\{\|x-y\|_\infty,|a-b|\}.
$$

Define dE(u,v) as the Hausdorff distance between E(u) and E(v) in this metric. Both endographs contain the whole baseline X times {0}; it must not be replaced by the images of the representative points. The metric is bounded above by one.

State coordinates are normalized. Changing spatial scales or the relative weight of the membership coordinate changes this metric and requires a corresponding certificate.

## 2 Representation and assumptions

Let P be a finite cover of X by compact cells. Overlap at cell boundaries is allowed. For every C in P, choose:

- A representative xC in C.
- An approximate trajectory yC,t in X for every t from zero through T.
- A stored membership grade aC in [0, 1].
- A certified grade error sigmaC satisfying |aC - mu0(xC)| at most sigmaC.
- A certified membership variation kappaC satisfying |mu0(x) - mu0(xC)| at most kappaC for all x in C.
- A certified upper membership value MC in [0, 1] satisfying mu0(x) at most MC for all x in C.
- A certified trajectory radius rhoC,t satisfying ||f^t(x) - yC,t|| infinity at most rhoC,t for all x in C.

These are whole cell assertions. Evaluating a function at several points does not establish any of them for the rest of the cell.

Define the finite approximation:

$$
\widehat\mu_t(y)=
\max\bigl(\{a_C:y_{C,t}=y,\ C\in P\}\cup\{0\}\bigr).
$$

Coincident locations use maximum, not sum or mean. Approximate states need not be normal: the representatives may miss a height one peak. No normalization is part of this definition.

## 3 P1 Endograph certificate

For each C and t, set

$$
v_C=\kappa_C+\sigma_C,\qquad
q_{C,t}=\max\{\rho_{C,t},v_C\}.
$$

A simple certificate is

$$
d_E(\mu_t,\widehat\mu_t)
\le
\min\left\{1,\max_{C\in P} q_{C,t}\right\}.
$$

A grade aware certificate is

$$
b_{C,t}
=
\min\left\{
\max\{M_C,a_C\},
\max\{\rho_{C,t},\kappa_C+\sigma_C\}
\right\},
\qquad
B_t=\max_{C\in P} b_{C,t}.
$$

**Proposed proposition P1.** Under the definitions and assumptions above, for every t from zero through T,

$$
d_E(\mu_t,\widehat\mu_t)\le B_t.
$$

### Proof argument

Take a point (y,a) in E(mu_t). If a is zero, the same point belongs to E(muHat_t). Suppose a is positive. The compact fiber maximum provides x with f^t(x) = y and mu0(x) at least a. Choose a cell C containing x.

The approximate endograph contains (yC,t, min(a,aC)). Its spatial separation from (y,a) is at most rhoC,t. The vertical separation is at most

$$
\max(0,a-a_C)
\le |\mu_0(x)-a_C|
\le \kappa_C+\sigma_C.
$$

This gives a distance at most qC,t. Independently, (y,0) is in the approximate endograph, giving distance at most a, which is at most MC. Thus this point has distance at most min(qC,t, MC), and hence at most bC,t.

For the reverse direction, take (y,a) in E(muHat_t). A zero height point is shared. If a is positive, a representative C attains the finite maximum at y = yC,t with a at most aC.

The true endograph contains

$$
(f^t(x_C),\min(a,\mu_0(x_C))).
$$

The spatial separation is at most rhoC,t and the vertical separation is at most sigmaC. The common baseline also gives distance at most aC. Therefore the distance is at most min(max(rhoC,t, sigmaC), aC), which is no greater than bC,t.

Taking both directed suprema proves the asserted bound. The simple certificate follows from MC and aC being at most one. End of internal argument.

### Interpretation

The radius controls spatial displacement. Initial membership variation and membership evaluation error control vertical displacement. Grades are transported without repeated resampling, so the vertical term is not multiplied by the number of time steps.

The grade cap explains why a low membership component can have a small endograph error despite a large spatial displacement. Alpha cut diagnostics are required when such components matter to the application.

## 4 P2 Trajectory enclosure

Start with yC,0 = xC and a certified radius rC,0 covering C around xC.

For every time k, let QC,k contain the true reachable set f^k(C) and the approximate representative yC,k. Suppose a verified constant LC,k satisfies

$$
\|f(z)-f(w)\|_\infty
\le L_{C,k}\|z-w\|_\infty
\quad\text{for all }z,w\in Q_{C,k},
$$

and a verified residual etaC,k satisfies

$$
\|y_{C,k+1}-f(y_{C,k})\|_\infty\le\eta_{C,k}.
$$

Then the recurrence

$$
r_{C,k+1}=L_{C,k}r_{C,k}+\eta_{C,k}
$$

is a valid trajectory radius, provided rC,k was valid. This follows from the triangle inequality by inserting f(yC,k).

A Jacobian bound must apply to a region on which the relevant mean value argument is valid, such as a convex box containing both sets. A derivative evaluated only at the representative is insufficient. Nondifferentiable tent maps use analytic Lipschitz bounds.

An interval image enclosure may instead produce a direct radius around the representative. Combining certified enclosures by intersection can tighten the result; sampling alone cannot.

For a global constant L,

$$
r_{C,t}\le
L^t r_{C,0}
+
\sum_{k=0}^{t-1}L^{t-1-k}\eta_{C,k}.
$$

All floating point arithmetic used for an upper bound must round outward or be covered by an explicit rigorous error term. A narrow looking interval computed with ordinary round to nearest operations is not automatically an enclosure.

## 5 P3 Refinement policy validity and convergence

### Validity under any completed policy

P1 depends on the final cover and its verified quantities. It does not depend on how the cells were selected. A learned policy, a random policy, or an adversarial policy therefore leaves P1 valid whenever it returns a complete cover with valid certificates.

This statement concerns validity, not termination or efficiency. Removing a cell without proving its contribution negligible breaks coverage and is not justified by the policy argument.

### Fixed horizon convergence target

For the basic convergence result, assume:

- f is globally Lipschitz on X with a finite verified L.
- mu0 is Lipschitz with a finite verified K.
- Representative arithmetic and grades are exact.
- Bisection is fair and makes the largest cell radius tend to zero.

The membership Lipschitz assumption applies over the entire relevant unit domain. Being Lipschitz only on a possibly disconnected support is insufficient for this step; the distinction is discussed in the [source review](RESEARCH_REVIEW.md).

If the largest initial cell radius is r, then rhoC,t can be bounded by L^t r and kappaC by K r. Consequently,

$$
\max_{0\le t\le T}d_E(\mu_t,\widehat\mu_t)
\le
\max\{1,L^T,K\}\,r.
$$

For fixed T, any positive tolerance can therefore be reached after sufficient fair refinement.

The factor one accounts for t = 0 and the case L less than one. The claim is finite horizon; it is not uniform over all time.

A FIFO fairness step every fifth selection eventually splits every active leaf. Longest side bisection in a fixed finite dimension makes the maximum diameter tend to zero: for any prescribed diameter only finitely many ancestral cells must be split, and fairness eventually processes each.

With fixed floating point precision, residuals and grade errors can impose a nonzero accuracy floor. A stronger arbitrary tolerance claim requires increasing precision or exact arithmetic. Budget and memory limits can prevent convergence in practice and must produce an incomplete result status.

### Important metric distinction

The familiar Lipschitz inheritance statements in levelwise metrics must not be silently transferred to the full endograph metric. Lifting a Lipschitz map as (x,a) to (f(x),a) gives a max(1,L) product bound because the membership coordinate is unchanged.

For example, a constant spatial map can preserve a difference in membership height. It therefore need not contract the whole endograph distance by its spatial Lipschitz constant zero. P2 propagates spatial radii only.

## 6 What remains to prove or review

| Item | Required evidence |
| --- | --- |
| Fiber maximum and composition | Check the exact function class and zero fiber convention |
| P1 | Review both directed distances, collisions, nonnormal states, grade caps, and the common baseline |
| P2 | Check enclosure domains, interval operations, and numeric error treatment |
| P3 | Check fairness, longest side refinement, precision assumptions, and the finite horizon restriction |
| Novelty | Compare theorem statements and constants with the 2007 and 2016 approximation papers and related set approximation results |
| Implementation | Link every mathematical quantity to a recomputable object and reject unsupported assumptions |
| Efficiency | Establish by experiments unless a separate restricted theorem is actually proved |

A certificate checker should use the public model definition, representative states, grades, partition, and numeric policy to recompute bounds. It must not trust bounds provided by the learned policy.

A small [exact rational feasibility check](../research/feasibility/certificate_finite_check.py) tested 240 finite instances and 1440 time slices in one and two dimensions. It found no violation of P1 or the spatial recurrence in those cases; the grade cap was strictly tighter than the simple bound on 627 time slices. A negative control shows that an invented zero radius can claim zero error when the true finite error is one. [Recorded output](../research/feasibility/certificate_finite_check.json).

Run this planning check from the project root with:

~~~text
python research/feasibility/certificate_finite_check.py
~~~

These are finite special cases with exact enumeration of cell bounds. They do not validate continuous enclosures, establish the continuous propositions, or measure the proposed application.

## 7 Attribution and claim status

Zadeh's extension principle, alpha cut images, endograph metrics, Lipschitz propagation, and learning to branch are established ideas. Background sources and their access status are recorded in [Related work](RELATED_WORK.md).

P1 to P3 organize a proposed certificate for this representation. The contribution under investigation is the complete method and its behavior across a declared benchmark class. A report must identify any statement already implied by earlier theory as an attributed specialization or corollary.

Use the actual completion and review dates for the 2026 proof draft. Do not label an empirical trend a proof or an internally derived proposition an independently validated new theorem.

## 8 Numerical foundation and deterministic CAE implementation

Milestone 01 implements exact finite endograph distances, directed interval operations, built in map enclosures, membership ranges, and verified trajectory box propagation. It tests positive cut identities and exact finite cases.

Milestone 02 implements P1 cell contributions and global envelopes, P2 local Lipschitz propagation tightened by direct interval images, deterministic horizon bound refinement with FIFO fairness, and retained best and last snapshots. The [method specification](CAE_METHOD.md) maps each mathematical quantity to its immutable Java record and describes stopping and replay.

The implementation review checked both directed distances in P1 against the full zero baseline and grade cap, the representative hull and outward evaluation residuals in P2, and the full cover and fairness conditions in P3. Exact finite references across all map families and an independently implemented continuous piecewise linear distance oracle provide test evidence. These tests do not replace independent mathematical review.

An exact two peak counterexample shows that splitting can increase the actual endograph error from 1/2 to 5/8. This is why the implementation retains the earliest best certificate rather than assuming monotonic improvement. The full worked example is in the method specification.

The fixed horizon convergence argument requires exact arithmetic or sufficiently vanishing numerical errors. The shipped solver has fixed configured precision and finite budgets; its budget exits do not contradict that conditional argument.

The Java numerical contract and verification evidence are linked from the [foundation report](milestones/00-01.md) and [milestone 02 report](milestones/02.md). Full text comparison of the closest 2007 and 2016 work remains open. No independent novelty or efficiency result follows from the implementation review.
