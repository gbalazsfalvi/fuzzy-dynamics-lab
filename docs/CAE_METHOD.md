# Certified Adaptive Extension: deterministic method

**Implementation date:** 17 November 2026.  
**Recipe:** cae-02-v1.  
**Review status:** internal derivation checked against the implementation and independent reference tests. Independent mathematical review and the closest literature comparison remain open.

This document specifies the implemented milestone 02 method. Its certificate covers the complete continuous initial domain and every integer time in a declared finite horizon. The method currently supports the four built in map families and tent peak membership profiles. It accepts no unverified black box model.

## Mathematical object

Let X = [0,1]^d with the infinity norm, with d equal to one or two. A continuous self map f transports an initial continuous membership function by the extension principle:

$$
\mu_t(y)=\sup\{\mu_0(x): f^t(x)=y\},
\qquad \sup\varnothing=0.
$$

The endograph contains every vertical segment below the membership function and the entire common zero baseline:

$$
E(\mu)=\{(x,a)\in X\times[0,1]:0\le a\le\mu(x)\}.
$$

Distances use the maximum of spatial infinity distance and membership height distance. All coordinates and membership grades are dimensionless. A membership grade is not a probability.

A binary partition covers the whole initial domain, including cells where membership is zero. Each cell C carries its midpoint xC, a stored lower membership grade aC, and a numerical representative trajectory yC,t. At each time the approximation is the finite fuzzy state at those representatives; collisions use maximum membership. Stored grades are transported unchanged.

## Membership quantities

The model provides a verified whole cell range [lC,uC], a verified range [pC,qC] at xC, and an upper bound K for its global Lipschitz constant. Set

$$
a_C=p_C,\qquad
\sigma_C=\operatorname{up}(q_C-p_C),\qquad M_C=u_C,
$$

$$
\kappa_C=\min\left\{
 \max\bigl(\operatorname{up}(u_C-p_C),\operatorname{up}(q_C-l_C)\bigr),
 \operatorname{up}(K\rho_{C,0})
\right\}.
$$

Here up denotes rounding toward positive infinity at the configured significant decimal precision. The initial radius rhoC,0 encloses C around xC.

Both candidates for kappaC bound the absolute variation from the representative's exact membership over the entire cell. Their minimum is consequently valid. SigmaC separately covers membership evaluation error. These terms are computed only at initialization of a cell, not multiplied at every trajectory step.

## Spatial enclosure

At time zero, the reachable box is C and the representative is its exact decimal midpoint. For the transition from t to t+1:

1. Take the Cartesian hull of the current reachable box and the numerical representative. This includes the representative even if previous rounding placed it outside the tightened box.
2. Evaluate a Lipschitz bound LC,t over that complete convex box.
3. Evaluate f on the singleton representative by directed intervals. Its exact decimal midpoint is yC,t+1. The radius of that singleton image around the midpoint bounds the representative evaluation error etaC,t.
4. Compute the upward rounded recurrence radius

$$
r^{\mathrm{prop}}_{C,t+1}
 =\operatorname{up}\left(
     \operatorname{up}(L_{C,t}\rho_{C,t})+\eta_{C,t}
   \right).
$$

5. Independently propagate the complete reachable box by interval evaluation. Its radius around yC,t+1 is the direct radius.
6. Take the smaller valid radius, intersect the direct image with its infinity norm ball, and measure the tightened radius again.

Both the direct enclosure and the recurrence enclosure contain the true image, so their intersection preserves coverage. A disjoint intersection is an arithmetic failure, never an excuse to fabricate a box. The saved CaeStep contains the direct radius, propagated radius, final radius, incoming Lipschitz bound, and evaluation residual.

No derivative at a single sample is substituted for a whole region Lipschitz bound.

## Endograph error certificate

For every cell and requested time, compute

$$
v_C=\operatorname{up}(\kappa_C+\sigma_C),
$$

$$
b_{C,t}=
\min\left\{
  \max(M_C,a_C),\
  \max(\rho_{C,t},v_C)
\right\},
\qquad
B_t=\max_{C}b_{C,t}.
$$

Under the stated enclosures, both directed distances satisfy

$$
d_E(\mu_t,\widehat\mu_t)\le B_t.
$$

The [proof note](PROOF_OBLIGATIONS.md) gives both directions of P1. Forward matching uses a representative from a cell containing a preimage. Reverse matching uses that representative's exact trajectory. The common baseline supplies the independent grade cap in both directions.

The success criterion is

$$
\max_{0\le t\le T}B_t\le\varepsilon.
$$

A small endograph error can hide a weak component: a cell with upper membership 0.01 contributes at most 0.01 even if its spatial enclosure is wide. This is a property of the metric. It does not guarantee that every positive alpha cut is approximated well.

## Deterministic refinement

The initial partition contains the complete unit box. Every committed refinement replaces one leaf by both closed children from an exact longest side bisection. Shared boundary points belong to both children. In a width tie, axis search starts at depth modulo dimension.

The maximum bound policy ranks a leaf by its largest contribution over the entire horizon. Ties select the oldest leaf; left children are created before right children. Every fifth selection instead takes the oldest eligible leaf. The fairness period is configurable and recorded. The uniform policy always takes the oldest eligible leaf.

The active partition maintains a priority queue and per time multisets of bounds. A split updates those envelopes without rescanning every leaf to select the maximum. Snapshots are immutable complete partitions.

The solver preserves the earliest snapshot attaining the smallest horizon bound seen so far. It also preserves the last completed partition. These are deliberately separate objects.

### A split can increase the actual error

Consider the identity time t = 0, with

$$
\mu_0(x)=\max\left\{
 \tfrac12\max(0,1-|x-\tfrac12|),\
 \max(0,1-20|x-\tfrac1{10}|)
\right\}.
$$

The root midpoint approximation has one atom at 1/2 of height 1/2. Its certificate is 1/2. At the narrow peak's top (1/10,1), the distance to that approximate endograph is exactly 1/2. The upper and lower witnesses agree, so its actual error is 1/2.

After one split, the midpoint atoms are at 1/4 and 3/4, both of height 3/8. The left cell certificate is 5/8. At the same narrow peak top the nearest approximate vertical segment is at distance 5/8. Again the upper and lower witnesses agree: the actual error has increased from 1/2 to 5/8.

This exact worked counterexample justifies retaining an incumbent even when all arithmetic is exact. The demonstration saves both partitions, and the tests check the rational lower witnesses. This is an internal example, not a claim that this phenomenon is a new discovery.

## Validity and convergence are separate

Any complete partition with valid cell quantities gives a valid P1 certificate, regardless of the selection policy.

With exact arithmetic, finite global Lipschitz constants L and K, and a fixed horizon T, a largest initial radius r gives the sufficient estimate

$$
\max_{0\le t\le T}d_E(\mu_t,\widehat\mu_t)
 \le \max(1,L^T,K)\,r.
$$

Periodic FIFO fairness eventually refines every active leaf when depth and resource limits are absent. Longest side bisection then makes the largest radius tend to zero. This supplies the fixed horizon convergence argument in P3.

The implemented run has finite budgets and a fixed decimal precision. It does not automatically increase precision. Evaluation residuals or grade errors can impose an accuracy floor, and expansive dynamics can require many cells. No guarantee of reaching every tolerance, terminating within a useful cost, or outperforming uniform refinement is claimed.

## Stopping, cancellation, and failure

| Status | Meaning |
| --- | --- |
| CERTIFIED | A complete retained snapshot meets tolerance at every requested time |
| BUDGET_EXHAUSTED | A complete upper bound is retained, but the requested tolerance was not reached |
| CANCELLED | A cooperative callback interrupted execution; complete earlier snapshots survive |
| NUMERICAL_FAILURE | A verified arithmetic step failed; complete earlier snapshots survive |

After checking tolerance and cancellation, the deterministic budget precedence is maximum completed refinements, maximum active cells, then maximum depth. The corresponding stop reason is saved separately.

A parent is replaced only after both child computations succeed. Cancellation or arithmetic failure during either child discards the unfinished split. If initialization itself fails, there is no complete snapshot and no certificate.

Counters record actual cell attempts, map interval images, spatial Lipschitz queries, membership ranges, and membership Lipschitz queries. They include attempted work in discarded refinements. They are not wall time estimates.

## Stored evidence and replay

Run from the project root:

~~~powershell
.\gradlew.bat caeDemo
.\gradlew.bat verifyCaeDemo
~~~

The first command creates artifacts/cae-demo.json and artifacts/cae-demo-summary.md. The second command reads the existing JSON without generating or overwriting it.

The seven examples include all map families, separated and weak peaks, a tight budget, a deliberately low precision run, and the nonmonotone refinement example. Outcome categories are recorded as observed.

The JSON has an explicit closed map registry and rational text parameters. Every BigDecimal is a JSON string, including scientific notation where applicable. JSON numeric tokens are rejected for decimal evidence.

Verification performs the following checks:

1. The source and build input SHA-256 digest matches the current local implementation.
2. The version, mathematical parameters, horizon, and arithmetic precision match the snapshot context.
3. The binary leaf paths cover the complete root exactly, with no duplicate paths, ancestor overlap, or missing subtree. The check collapses sibling prefixes; it does not estimate volume numerically.
4. Geometry, membership, trajectories, cell contributions, and global envelopes are recomputed for both saved snapshots.
5. The complete deterministic run is repeated to check its selection history, incumbent, stopping condition, and operation counters.

The snapshot verifier is intentionally strict: it accepts this recipe's canonical evidence, rather than arbitrary looser valid bounds. It shares trusted interval primitives and the cell recipe with generation. It is not an independently implemented proof checker or a formal verification system. Independent exact and analytic tests address that shared implementation risk.

The digest records provenance. It provides no authentication and is not a digital signature. Archive metadata timestamps are separate from the deterministic mathematical result.

## Code map

| Mathematical or execution concept | Core type |
| --- | --- |
| Supported f, mu0, T | CaeProblem |
| Precision, tolerance, budgets, policy | CaeSettings |
| Canonical initial partition identity | CellPath |
| Stored grade, sigma, kappa, M | MembershipSample |
| Reachable box, representative, rho, L, eta | CaeStep |
| Cell and per time contributions | CaeCell |
| Complete partition and global bounds | CaeSnapshot |
| Best, last, status, history, actual work | CaeResult |
| Deterministic orchestration | CaeSolver |
| Context, exact coverage, numerical replay | CertificateVerifier |

The implementation remains local. Comparative baselines, a learned policy, a workbench, and a research novelty claim remain later work.
