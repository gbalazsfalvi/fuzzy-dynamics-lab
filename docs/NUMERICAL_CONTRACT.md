# Numerical and CAE contract

The foundation supports discrete maps on the unit interval or unit square. Exact Rational values define model parameters and small reference calculations. Decimal intervals enclose exact values using directed rounding; no binary floating point conversion is used.

## Arithmetic

A Rational has a positive denominator and is reduced to lowest terms. Decimal input is interpreted exactly, including scientific notation. Division by zero is rejected.

A DecimalInterval is closed and nonempty. Its endpoints are finite BigDecimal values in increasing order. DecimalArithmetic records a positive decimal precision. Every operation rounds the lower endpoint with FLOOR and the upper endpoint with CEILING. Denominators containing zero are unsupported. Squaring treats intervals crossing zero specially.

## Geometry and maps

Points and boxes have an explicit positive dimension and immutable coordinates. Map and membership inputs must be wholly contained in the corresponding unit domain. Domain intersection of a map image is justified only by the proved self map property of the built in map.

The supported maps are affine contraction, tent, logistic with parameter in [0,4], and a convex coupling of two tent maps. Each map provides interval images, a Lipschitz bound valid over the supplied box, and exact rational evaluation for small references.

## Membership and finite references

Tent peaks and maxima of peaks provide continuous membership profiles with certified interval ranges and Lipschitz bounds. Profiles can have nonconvex alpha cuts. Grades lie in [0,1].

Finite fuzzy states use exact points and grades for reference calculations. Collisions use maximum. A positive alpha cut is a complete set of eligible points, never its convex hull. The zero state may have no atoms.

The finite endograph distance uses the max product metric and the entire zero baseline over the unit domain. Empty positive cuts are represented as empty collections; no nonempty set Hausdorff distance is silently applied to them.

## Verified trajectory enclosures

A foundation trajectory record includes its reachable box, a chosen representative, and the representative evaluation residual. Interval propagation encloses all exact trajectories starting in the initial box.

The CAE layer additionally propagates a local Lipschitz radius, intersects it with the direct interval enclosure, and records every term of that recurrence. The Lipschitz region contains both the full reachable box and the numerical representative. All upper bound arithmetic rounds toward positive infinity.

## CAE invariants

A finite nonnegative horizon, an explicit significant decimal precision, a tolerance strictly between zero and one, and finite refinement budgets define a run. Only the built in map and membership families are accepted. Unsupported model definitions fail validation before a run.

The initial cell paths cover the complete unit domain. Longest side dyadic bisections use depth rotated tie breaks. Both child computations must complete before a parent is removed. Shared cell boundaries are retained. A zero membership cell is not silently deleted.

Each cell stores a lower representative grade, its evaluation error, a whole cell absolute membership variation bound, and an upper membership bound. Grades are transported unchanged. The maximum product endograph bound combines those quantities with the spatial enclosure radius and the common baseline grade cap.

Success requires the bound to meet tolerance at every requested integer time, including zero. The solver retains both the earliest best bound snapshot and the last complete partition, since refinement need not improve either the bound or actual error. Resource exhaustion, cancellation, and numerical failure never claim tolerance success.

Certificate replay binds the recipe version, exact mathematical problem, and precision; checks exact prefix coverage; and recomputes every cell and aggregate bound. It is a canonical replay checker sharing the trusted numerical recipe, not a separate formal proof implementation. The CLI also replays the complete selection trace and checks source provenance.

The full equations and replay format are specified in [CAE method](CAE_METHOD.md).

## Verification boundaries

Exact rational and analytic solutions supply independent references for interval checks. Deterministic randomized cases test additional inputs. Passing these checks is not a proof of the complete implementation.

The deterministic method is implemented with fixed precision. Arbitrary tolerance convergence requires exact or suitably increasing precision and unbounded fair refinement under the stated Lipschitz assumptions; finite runs need not attain it. Tests include an independent continuous piecewise linear endograph oracle, exact finite cases, invalid coverage, and modified certificate data.

The current milestone does not implement learned refinement, automatic precision escalation, published comparison algorithms, a Spring Boot API, or a frontend. The evidence does not establish scientific novelty or comparative speedup.
