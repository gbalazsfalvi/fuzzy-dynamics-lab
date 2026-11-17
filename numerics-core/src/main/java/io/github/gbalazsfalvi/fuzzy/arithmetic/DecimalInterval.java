package io.github.gbalazsfalvi.fuzzy.arithmetic;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A closed, finite, nonempty decimal interval with scale independent value equality.
 *
 * @param lower the inclusive lower endpoint
 * @param upper the inclusive upper endpoint
 */
public record DecimalInterval(BigDecimal lower, BigDecimal upper) {
  /**
   * Validates and canonicalizes the endpoints.
   *
   * @param lower the lower endpoint
   * @param upper the upper endpoint
   * @throws IllegalArgumentException if the endpoints are reversed
   */
  public DecimalInterval {
    lower = Objects.requireNonNull(lower, "lower").stripTrailingZeros();
    upper = Objects.requireNonNull(upper, "upper").stripTrailingZeros();
    if (lower.compareTo(upper) > 0) {
      throw new IllegalArgumentException("An interval lower endpoint must not exceed its upper.");
    }
  }

  /**
   * Creates an interval from exact decimal strings.
   *
   * @param lower the inclusive lower endpoint
   * @param upper the inclusive upper endpoint
   * @return the interval
   */
  public static DecimalInterval of(String lower, String upper) {
    return new DecimalInterval(new BigDecimal(lower), new BigDecimal(upper));
  }

  /**
   * Creates a singleton without rounding.
   *
   * @param value the exact decimal
   * @return the singleton interval
   */
  public static DecimalInterval point(BigDecimal value) {
    return new DecimalInterval(value, value);
  }

  /**
   * Tests decimal containment.
   *
   * @param value the exact decimal
   * @return whether the interval contains the value
   */
  public boolean contains(BigDecimal value) {
    return lower.compareTo(value) <= 0 && upper.compareTo(value) >= 0;
  }

  /**
   * Tests exact rational containment without rounding the reference.
   *
   * @param value the exact reference
   * @return whether the interval encloses it
   */
  public boolean contains(Rational value) {
    return Rational.fromDecimal(lower).compareTo(value) <= 0
        && Rational.fromDecimal(upper).compareTo(value) >= 0;
  }

  /**
   * Tests enclosure of an entire interval.
   *
   * @param other the interval to contain
   * @return whether both endpoints are enclosed
   */
  public boolean contains(DecimalInterval other) {
    return contains(other.lower) && contains(other.upper);
  }

  /**
   * Computes the smallest interval containing both operands.
   *
   * @param other the second interval
   * @return the hull
   */
  public DecimalInterval hull(DecimalInterval other) {
    return new DecimalInterval(lower.min(other.lower), upper.max(other.upper));
  }

  /**
   * Intersects two closed intervals.
   *
   * @param other the second interval
   * @return the intersection, or an empty optional when disjoint
   */
  public Optional<DecimalInterval> intersection(DecimalInterval other) {
    BigDecimal lo = lower.max(other.lower);
    BigDecimal hi = upper.min(other.upper);
    return lo.compareTo(hi) <= 0 ? Optional.of(new DecimalInterval(lo, hi)) : Optional.empty();
  }

  /**
   * Computes an exact midpoint of finite decimal endpoints.
   *
   * @return a point inside the interval
   */
  public BigDecimal midpoint() {
    return lower.add(upper).divide(BigDecimal.TWO);
  }
}
