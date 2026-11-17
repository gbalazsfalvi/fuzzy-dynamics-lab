# Milestone 00 source review and research contract

**Review date:** 17 November 2026.

This is the milestone 00 source-review record. The later [comparison implementation](COMPARISON_METHOD.md) includes the eligible exact PWL transport specialization discussed below; the original review's future-tense wording is retained as history. The closest-full-text and external mathematical review limits remain current.

The accessible primary literature supports implementing the numerical foundation. The full text comparison required for a defensible originality claim remains open for the 2007 decomposition paper and the 2016 approximation paper. Their abstracts and previews do not establish the missing algorithm details.

## Reviewed results and their implications

### Roman Flores and colleagues 2001

[A note on Zadeh's extensions](https://victorayala.cl/wp-content/uploads/88ref.pdf) was reviewed in full, including the displayed statements on printed pages 328 and 329. Theorem 2.1 gives the alpha cut image identity in its declared function class; Theorem 3.3 addresses continuity in the levelwise metric. Theorem 4.1 concerns a local approximation of extensions under additional assumptions.

The core tests the positive cut identity on finite references. It does not treat continuity in one fuzzy metric as an identical numerical bound in another.

### Chalco Cano and colleagues 2011

[On the approximation of compact fuzzy sets](https://victorayala.cl/wp-content/uploads/36ref.pdf) was reviewed in full. Section 3 corrects an earlier convolution assertion by distinguishing Lipschitz behavior on a support from behavior over the whole ambient space. Theorem 2 gives a core preserving approximation with a levelwise error bound of 1/p under its construction.

The initial CAE convergence assumptions therefore require a membership bound over the entire relevant unit domain. The Java peak families provide such a bound. The convolution construction is prior art, not a new result of this project.

### Kupka and Skorupova 2021

[The complete article](https://www.mdpi.com/2227-7390/9/21/2737) was reviewed, particularly Sections 1.2, 4.1, 5, and 6. Its algorithm linearizes the scalar map, partitions at turning points, transports linear pieces, and reconstructs membership by pointwise maxima. The comparison uses discretized trajectory information.

The foundation preserves maxima at collisions and keeps reference quality labels explicit. An interval enclosure or exact finite distance is different from a sampled continuous profile comparison. The paper is a relevant future one dimensional baseline.

### Skorupova and colleagues 2022

[The complete article](https://www.mdpi.com/2227-7390/10/5/808) was reviewed, including the definitions and conclusion. Automatic selection of linearization segments and discretization points is already studied. Its two dimensional graphs describe scalar interval functions; they do not establish a general two state variable fuzzy solver.

The planned comparison must include strong deterministic or adaptive preprocessing, rather than assume that automatic refinement is itself new.

## Sources with incomplete access

| Source | Material inspected | Remaining work |
| --- | --- | --- |
| Kloeden 1982, [Fuzzy dynamical systems](https://doi.org/10.1016/0165-0114(82)90056-2) | Abstract, author record, and repository metadata | Full article for detailed historical theorem attribution |
| Chalco Cano and colleagues 2007, [The extension principle and a decomposition of fuzzy sets](https://doi.org/10.1016/j.ins.2007.07.007) | Publisher abstract, introduction, section previews, university metadata | Full algorithm and proof comparison |
| Chalco-Cano and colleagues 2008, [Multivariate decomposition](https://doi.org/10.1016/j.fss.2008.06.011) | Abstract, metadata and public article transcription | Printed-formula verification and complete method comparison |
| Kupka 2016, [On approximations of Zadeh's extension principle](https://doi.org/10.1016/j.fss.2015.03.014) | Publisher abstract, introduction, section previews, author publication portal, and open access metadata | Full algorithm, metric convention, and constant comparison |

The 2007 previews already establish that decomposition and local linear approximation with error bounds precede CAE. The 2016 previews establish a general approximation procedure and metric dependent simplification. Neither preview is sufficient for claiming that CAE is mathematically distinct.

Publisher full text routes were inaccessible in this environment. OpenAlex reported no open full text location for the 2007 and 2016 records. No subscriptions were purchased, no authors were contacted, and no restricted content was bypassed.

A final public repository search also inspected the University of Seville's author catalogue and idUS discovery API. Exact-title/DOI phrase queries returned no matching full text. The additional 2008 abstract describes multivariate decomposition; it reinforces the restriction against a broad first-approximation claim. This does not establish detailed equivalence to CAE.

A subsequent public transcription of the 2008 article exposed its levelwise
metric and tangent-based construction. Its mathematical OCR is damaged; this
inspection does not close printed-formula or constant verification. No article
PDF was redistributed, login requested or restricted download bypassed. The
source register retains that narrower review status.

Downloaded source material is kept in a separate local research cache. The project contains source URLs, access records, and original analysis rather than redistributed article PDFs.

## Mathematical contract frozen for the foundation

The foundation fixes these conventions:

1. Discrete time maps on a normalized compact unit interval or unit square.
2. Spatial infinity norm and max product endograph distance.
3. A complete zero baseline over the common universe.
4. Exact textual rational parameters and directed decimal enclosures.
5. Nonnegative membership grades combined by maximum.
6. Complete positive alpha cuts, including empty cuts and nonconvex support.
7. Whole region bounds, with fixed finite horizon convergence assumptions.
8. Separate statuses for a mathematical argument, sampled evidence, and scientific novelty.

Under a continuous scalar map, an interval alpha cut has an interval image. Thus a convex one dimensional initial profile does not acquire a disconnected positive cut merely by repeated continuous mapping. Nonconvex test inputs are specified explicitly; higher dimensional images require separate geometry analysis. This follows directly from the intermediate value property and is a modeling check for our benchmarks.

The source articles use different spaces, normality assumptions, and conventions. These are mapped explicitly rather than merged into one unspecified notion of a fuzzy number.

## Contribution statement

CAE will investigate adaptive refinement of the initial partition using finite representatives, trajectory enclosures, membership variation, and a finite horizon objective. A learned policy may improve selection; verified arithmetic supplies numerical bounds.

The foundation implements primitives needed to evaluate that proposal. It does not implement the complete adaptive method, demonstrate a speedup, or establish a new theorem.

P1 in the proof note remains an internal derivation. Its relation to established covering and extension approximation arguments must be audited against the full closest papers before a novelty claim.

## Baseline eligibility

| Baseline | Eligible comparison | Restriction |
| --- | --- | --- |
| Uniform finite representatives | All declared built in maps and membership families | Same arithmetic and later certificate engine as CAE |
| Deterministic adaptive strategies | All declared CAE cases | Same bounds, budget, and fairness |
| Alpha cut propagation with full unions | Cases with a validated representation and error bound | No convex hull substitution for disconnected cuts |
| 2021 PSO simulation | One dimensional continuous maps with a supported piecewise linear input | Reproduction and cost accounting remain milestone 03 work |
| 2022 tuning procedure | Scalar map preprocessing | Not a complete two dimensional fuzzy trajectory baseline |
| 2007 decomposition | The fuzzy interval and regularity assumptions established by the full method | Detailed eligibility remains provisional |
| 2016 approximation | To be finalized after full text review | Do not infer implementation details from the preview |
| ZEP | Supported fuzzy number examples | No assumption of arbitrary joint nonconvex state support |

## Milestone 00 disposition

The research contract, source register, contribution ledger, and initial baseline rules are delivered. The original full text exit condition for milestone 00 is still open. Milestone 01 can be implemented and tested using the explicitly documented mathematical contract without claiming that this source access condition has been satisfied.
