package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import java.util.Comparator;
import java.util.List;

/**
 * Exact continuous endograph distance between a finite state and an alpha grid staircase.
 *
 * <p>Each nonempty level contributes its full interval union times the vertical segment from zero
 * to that level. Directed interval coverage is optimized by activating target atoms in membership
 * order. No spatial raster or CAE error formula enters the calculation.
 */
public final class AlphaGridEndograph {
  private AlphaGridEndograph() {}

  /**
   * Measures the exact distance to the stored staircase, whose reference error is separate.
   *
   * @param finite the scalar finite approximation
   * @param reference the exact cut trajectory defining the lower staircase
   * @param time the requested stored time
   * @return the exact continuous distance to the staircase
   * @throws IllegalArgumentException if dimensions or time are unsupported
   */
  public static Rational distance(FiniteFuzzySet finite, AlphaCutTrajectory reference, int time) {
    if (finite.dimension() != 1 || time < 0 || time >= reference.states().size()) {
      throw new IllegalArgumentException(
          "Staircase distance requires scalar states and a valid time.");
    }
    List<IntervalUnion> cuts = reference.states().get(time);
    List<FuzzyAtom> atoms =
        finite.atoms().stream().sorted(Comparator.comparing(FuzzyAtom::grade).reversed()).toList();
    Rational forward = Rational.ZERO;
    for (int level = 0; level < reference.levels().size(); level++) {
      Rational height = reference.levels().get(level);
      IntervalUnion source = cuts.get(level);
      if (source.intervals().isEmpty()) {
        continue;
      }
      Rational nearest = height; // Full common baseline.
      ActivePointCoverage active = new ActivePointCoverage(source);
      for (FuzzyAtom atom : atoms) {
        Rational x = atom.location().coordinate(0);
        active.insert(x);
        Rational vertical = height.subtract(atom.grade()).max(Rational.ZERO);
        Rational spatial = active.maximumDistance();
        nearest = nearest.min(vertical.max(spatial));
      }
      forward = forward.max(nearest);
    }

    Rational reverse = Rational.ZERO;
    for (FuzzyAtom atom : atoms) {
      Rational nearest = atom.grade();
      for (int level = 0; level < reference.levels().size(); level++) {
        IntervalUnion cut = cuts.get(level);
        if (!cut.intervals().isEmpty()) {
          Rational vertical =
              atom.grade().subtract(reference.levels().get(level)).max(Rational.ZERO);
          nearest = nearest.min(cut.distance(atom.location().coordinate(0)).max(vertical));
        }
      }
      reverse = reverse.max(nearest);
    }
    return forward.max(reverse);
  }
}
