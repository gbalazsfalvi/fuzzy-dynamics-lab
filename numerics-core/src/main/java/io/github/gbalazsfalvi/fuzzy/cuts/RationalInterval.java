package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.Objects;

/**
 * An exact closed interval inside the normalized one dimensional state domain.
 *
 * @param lower the left endpoint in [0,1]
 * @param upper the right endpoint in [lower,1]
 */
public record RationalInterval(Rational lower, Rational upper) {
  /**
   * Validates endpoint order and the complete unit domain.
   *
   * @param lower the left endpoint
   * @param upper the right endpoint
   * @throws IllegalArgumentException if the interval is reversed or outside [0,1]
   */
  public RationalInterval {
    Objects.requireNonNull(lower, "lower");
    Objects.requireNonNull(upper, "upper");
    if (lower.compareTo(Rational.ZERO) < 0
        || upper.compareTo(Rational.ONE) > 0
        || lower.compareTo(upper) > 0) {
      throw new IllegalArgumentException("An exact cut interval must lie inside [0,1].");
    }
  }

  /**
   * Tests closed interval membership, including shared endpoints.
   *
   * @param point the exact coordinate
   * @return whether the coordinate lies in the interval
   */
  public boolean contains(Rational point) {
    return lower.compareTo(point) <= 0 && upper.compareTo(point) >= 0;
  }

  /**
   * Computes the exact distance from a real coordinate to the interval.
   *
   * @param point the coordinate, which may be outside the unit domain
   * @return the nonnegative distance
   */
  public Rational distance(Rational point) {
    return lower.subtract(point).max(point.subtract(upper)).max(Rational.ZERO);
  }
}
