package io.github.gbalazsfalvi.fuzzy.geometry;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable finite decimal point whose equality ignores decimal scale.
 *
 * @param coordinates the nonempty ordered coordinate list
 */
public record DecimalPoint(List<BigDecimal> coordinates) {
  /**
   * Copies and canonicalizes the coordinates.
   *
   * @param coordinates the nonempty coordinate list
   * @throws IllegalArgumentException if the list is empty
   */
  public DecimalPoint {
    coordinates = coordinates.stream().map(BigDecimal::stripTrailingZeros).toList();
    if (coordinates.isEmpty()) {
      throw new IllegalArgumentException("A point must have at least one coordinate.");
    }
  }

  /**
   * Parses finite decimal coordinates exactly.
   *
   * @param values the textual coordinates
   * @return the point
   */
  public static DecimalPoint of(String... values) {
    return new DecimalPoint(Arrays.stream(values).map(BigDecimal::new).toList());
  }

  /**
   * Returns the coordinate count.
   *
   * @return the positive dimension
   */
  public int dimension() {
    return coordinates.size();
  }

  /**
   * Returns a coordinate.
   *
   * @param axis the zero based axis
   * @return the exact decimal coordinate
   */
  public BigDecimal coordinate(int axis) {
    return coordinates.get(axis);
  }

  /**
   * Converts finite decimals into exact fractions without rounding.
   *
   * @return the same point represented rationally
   */
  public ExactPoint toExact() {
    return new ExactPoint(
        coordinates.stream()
            .map(io.github.gbalazsfalvi.fuzzy.arithmetic.Rational::fromDecimal)
            .toList());
  }
}
