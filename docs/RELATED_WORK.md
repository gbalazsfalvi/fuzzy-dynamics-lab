# Related work and novelty audit

**Audit date:** 17 November 2026.

The proposed contribution is a particular certified, adaptive fuzzy trajectory method and its empirical evaluation. The current evidence supports investigating it. It does not establish a first implementation claim or a new general theorem.

## Primary sources

| Source | Established contribution and relevance | Access inspected |
| --- | --- | --- |
| Kloeden, 1982, Fuzzy dynamical systems. [DOI](https://doi.org/10.1016/0165-0114(82)90056-2) | Early axiomatic fuzzy dynamics, trajectories, and a fuzzy state space interpretation | Publisher abstract and author uploaded record; full article not read |
| Román-Flores, Barros, Bassanezi, 2001, A note on Zadeh's extensions. [Author hosted PDF](https://victorayala.cl/wp-content/uploads/88ref.pdf) | Continuity and approximation results for extensions using levelwise distances | Full five page article available and inspected |
| Chalco-Cano and colleagues, 2007, The extension principle and a decomposition of fuzzy sets. [DOI](https://doi.org/10.1016/j.ins.2007.07.007) | Decomposition of fuzzy intervals, local linear approximation, approximation bounds, and examples; a close predecessor for cell based methods | Publisher abstract, introduction, and section previews; full text still required |
| Chalco-Cano and colleagues, 2008, An approximation to the extension principle using decomposition of fuzzy intervals. [Public article transcription](https://www.academia.edu/25777805/An_approximation_to_the_extension_principle_using_decomposition_of_fuzzy_intervals) | Multivariate decomposition and local linearization are prior subjects | Abstract, metadata and transcription inspected; damaged mathematical OCR still requires printed-formula verification |
| Chalco-Cano and colleagues, 2011, On the approximation of compact fuzzy sets. [Full article](https://victorayala.cl/wp-content/uploads/36ref.pdf) | Lipschitz fuzzy approximations by sup min convolution and differentiable fuzzy interval approximations | Full nine page article reviewed, including the support versus ambient Lipschitz distinction and Theorem 2 |
| Kupka, 2016, On approximations of Zadeh's extension principle. [DOI](https://doi.org/10.1016/j.fss.2015.03.014) | General approximation procedures, F transform connections, and metric dependent simplification using finitely many closed sets | Publisher abstract, introduction, and section previews; full text still required |
| Kupka and Škorupová, 2021, On PSO-Based Simulations of Fuzzy Dynamical Systems Induced by One-Dimensional Ones. [Full article](https://www.mdpi.com/2227-7390/9/21/2737) | Implemented interval map simulation using piecewise linear approximations and PSO; supports more general profiles than fuzzy numbers | Full article body inspected |
| Škorupová, Raunigr, Bujok, 2022, Usage of Selected Swarm Intelligence Algorithms for Piecewise Linearization. [Full article](https://www.mdpi.com/2227-7390/10/5/808) | Automatic linearization parameter selection is already studied; it must be treated as prior work | Publisher article and reprint inspected |
| López-Martínez, 2026 journal version, Topological dynamics for the endograph metric I: Equivalences with other metrics. [Author preprint](https://arxiv.org/abs/2510.17990) | Recent theoretical results about endograph dynamics and relations with other metrics; not a workbench implementation | Abstract and publication metadata inspected |
| Lu and Kumar, 2019 preprint, Neural Network Branching for Neural Network Verification. [Author preprint](https://arxiv.org/abs/1912.01329) | Learned branching within a verification procedure; an important adjacent precedent for separating policy and bounds | Abstract and initial paper text inspected |
| Deep reinforcement learning for adaptive mesh refinement, 2023. [Publisher article](https://www.sciencedirect.com/science/article/abs/pii/S002199912300476X) | Learned refinement for numerical methods is an established adjacent topic; distinguishes empirical indicators from error guarantees | Publisher abstract and introductory preview inspected |

## Existing software

| Software | Relevant capability | Comparison boundary |
| --- | --- | --- |
| [ZEP](https://cran.r-project.org/web/packages/ZEP/index.html) | Computes and visualizes extension principle approximations for fuzzy numbers | Applicable to eligible fuzzy number cases, not automatically to arbitrary nonconvex states |
| [fuzzySetOperationNN](https://github.com/samkreter/fuzzySetOperationNN) | Public neural approximation experiments for fuzzy arithmetic | Rules out a broad claim to invent neural fuzzy arithmetic |
| [CORA](https://tumcps.github.io/CORA/) | Established reachable set and verification tooling, including discrete time systems | Strong adjacent numerical reference; uncertainty semantics and output representation differ |

## Historical implementation gap

Section 1.2 of the 2021 Kupka and Škorupová article states that the 2016 proposal had no implementation at the time. That is a dated claim about a particular proposal.

It does not establish that all fuzzy dynamics lacked implementations, nor that the specific proposal remains unimplemented everywhere in November 2026. CAE will be assessed on its exact algorithm, assumptions, comparisons, and results rather than relying on that historical gap.

## Closest comparison tasks

Before asserting originality:

1. Obtain and analyze the full 2007 and 2016 papers, including definitions, algorithms, constants, and proofs; include the additional 2008 multivariate comparison identified during the final source-access pass.
2. Find subsequent implementations and citations of the relevant procedures.
3. Compare the finite representative certificate with standard covering and Hausdorff approximation arguments.
4. Compare full horizon refinement with existing trajectory enclosure and adaptive reachability methods.
5. Compare the learned policy with existing learning to branch and adaptive numerical methods.
6. Record each difference as mathematical, algorithmic, implementation related, or experimental.

For each comparison, record the exact theorem or algorithm number, supported dimension, membership assumptions, metric convention, time horizon, computable bounds, numeric precision, and public implementation.

The endograph product metric may use a maximum, sum, or Euclidean distance in different papers. Equivalent topologies do not imply identical numerical constants. Compare formulas using the declared max metric before claiming a tighter bound.

## Contribution ledger

| Proposed claim | Current status | Evidence needed |
| --- | --- | --- |
| CAE is a specified method with a clear representation | Implemented and documented | [Method](CAE_METHOD.md), numerical tests and saved replay |
| Its certificate is valid under declared assumptions | Internal derivation and implementation checks; external review open | Whole-domain premises, directed arithmetic, exact tests and canonical replay |
| Its adaptive strategy is more efficient | Measured within the declared cohort only | [Evaluation](milestones/05.md); strongest validation-selected deterministic policy retained |
| Learning improves on strong deterministic selection | Not supported by the frozen primary result | Ridge 67/120 versus maximum-bound 78/120; conditional work/time and limits reported |
| A proof or bound is new to the literature | Unestablished | Full closest work comparison and expert assessment |
| This is the first implementation of an older theory | Not claimed | The historical evidence does not justify this statement |

## Research decisions

If the certificate is already an immediate corollary of a prior result, attribute it and retain the engineering and empirical study. Search for a sharper computable local bound only where it addresses a demonstrated limitation.

If learning adds no benefit, report that result and the conditions under which it occurs. A valid numerical method and a carefully designed negative comparison remain useful deliverables, but the project description must reflect what was actually established.
