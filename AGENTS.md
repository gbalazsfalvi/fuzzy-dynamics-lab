# Fuzzy Dynamics Lab operating guide

Read README.md, docs/NUMERICAL_CONTRACT.md, and docs/milestones/00-01.md before changing the numerical foundation. The research plan, proof obligations, experiment protocol, and related work live under docs.

For CAE changes, also read docs/CAE_METHOD.md and docs/milestones/02.md. Preserve complete partition coverage and atomic two-child replacement. Keep best and last snapshots separate: a split can increase the actual error. Run caeDemo and then verifyCaeDemo after numerical changes; verification must read the saved artifact without regenerating it.

For comparisons, read docs/COMPARISON_METHOD.md and the selected executable protocol. Preserve complete paired cohorts, unavailable methods, resource exits, exact string evidence, and the distinction between a certificate and an observed reference error. Do not tune a method on held out results. Scalar references do not imply joint two dimensional geometry coverage.

## Scope

This is a separate research project. All source code, comments, interfaces, reports, and documentation are in English. The numerical API is a local loopback application. Source/release publication and static portfolio presentation are separate from exposing that API as a remote service.

## Numerical invariants

- Keep numerics-core free of frameworks and third-party runtime dependencies.
- Represent exact reference quantities with Rational. Do not convert model parameters through binary floating point.
- Interval lower bounds round toward negative infinity; upper bounds round toward positive infinity.
- Validate the full input box, its dimension, and the declared unit domain.
- Preserve nonconvex fuzzy states and combine colliding memberships with maximum.
- Endographs share the complete zero baseline. Membership is not probability.
- A sampled check is evidence for tested cases, not a continuous proof.
- Record source access and scientific claim status accurately. Do not infer missing theorem details from an abstract.

## Development

Use the Gradle wrapper with a Java 21 toolchain. Run spotlessApply after Java edits and check before completing changes. Public types and members require English Javadoc explaining assumptions, precision, and failure conditions.

The numerical core has a 95 percent line and 90 percent branch coverage gate. Preserve independent exact or analytic references when extending it.

Tests should use independent analytic or exact rational references and exercise invalid assumptions. Do not add a benchmark result or a novelty claim without its supporting evidence.

NVM is available only from CMD and PowerShell. Do not use NVM scripts.
