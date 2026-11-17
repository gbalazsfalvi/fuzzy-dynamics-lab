package io.github.gbalazsfalvi.fuzzy.pwl;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.Objects;

/**
 * An exact graph vertex for a scalar piecewise linear map or membership function.
 *
 * @param coordinate the unit domain coordinate
 * @param value the unit range value
 */
public record LinearKnot(Rational coordinate, Rational value) {
  /**
   * Validates both unit coordinates.
   *
   * @param coordinate the input in [0,1]
   * @param value the output in [0,1]
   * @throws IllegalArgumentException if either coordinate is outside the unit interval
   */
  public LinearKnot {
    Objects.requireNonNull(coordinate, "coordinate");
    Objects.requireNonNull(value, "value");
    if (coordinate.compareTo(Rational.ZERO) < 0
        || coordinate.compareTo(Rational.ONE) > 0
        || value.compareTo(Rational.ZERO) < 0
        || value.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Piecewise linear vertices must lie in the unit square.");
    }
  }
}
