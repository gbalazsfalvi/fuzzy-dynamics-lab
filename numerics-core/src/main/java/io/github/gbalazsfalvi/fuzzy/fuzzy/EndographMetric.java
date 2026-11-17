package io.github.gbalazsfalvi.fuzzy.fuzzy;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.List;

/**
 * Exact Hausdorff distance of finite fuzzy endographs in the max product metric.
 *
 * <p>The common baseline is the entire unit domain at height zero, not just the listed atoms.
 * Distance along a source vertical segment is nondecreasing in height, so checking its top
 * suffices.
 */
public final class EndographMetric {
  private EndographMetric() {}

  /**
   * Computes the exact symmetric distance, including empty zero states.
   *
   * @param left the first finite state
   * @param right the equally dimensioned second state
   * @return the exact endograph distance in [0,1]
   * @throws IllegalArgumentException if dimensions differ
   */
  public static Rational distance(FiniteFuzzySet left, FiniteFuzzySet right) {
    if (left.dimension() != right.dimension()) {
      throw new IllegalArgumentException("Endograph metric dimensions must agree.");
    }
    return directed(left, right).max(directed(right, left));
  }

  private static Rational directed(FiniteFuzzySet source, FiniteFuzzySet target) {
    Rational maximum = Rational.ZERO;
    List<FuzzyAtom> targets = target.atoms();
    for (FuzzyAtom from : source.atoms()) {
      // The point directly beneath the source top always belongs to the common zero baseline.
      Rational nearest = from.grade();
      for (FuzzyAtom to : targets) {
        Rational vertical = from.grade().subtract(to.grade()).max(Rational.ZERO);
        Rational candidate = from.location().distanceInfinity(to.location()).max(vertical);
        nearest = nearest.min(candidate);
      }
      maximum = maximum.max(nearest);
    }
    return maximum;
  }
}
