package io.github.gbalazsfalvi.fuzzy.geometry;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;

/** Validates the declared normalized domain at map and membership boundaries. */
public final class UnitDomain {
  private UnitDomain() {}

  /**
   * Requires an entire box to lie in the declared unit cube.
   *
   * @param box the candidate input
   * @param dimension the expected dimension
   * @throws IllegalArgumentException if dimensions differ or any point is outside the cube
   */
  public static void require(Box box, int dimension) {
    if (box.dimension() != dimension || !Box.unit(dimension).contains(box)) {
      throw new IllegalArgumentException(
          "The entire input box must lie in its declared unit domain.");
    }
  }

  /**
   * Requires an exact point to lie in the declared unit cube.
   *
   * @param point the candidate input
   * @param dimension the expected dimension
   * @throws IllegalArgumentException if dimensions differ or a coordinate is outside [0,1]
   */
  public static void require(ExactPoint point, int dimension) {
    if (point.dimension() != dimension
        || point.coordinates().stream()
            .anyMatch(
                value -> value.compareTo(Rational.ZERO) < 0 || value.compareTo(Rational.ONE) > 0)) {
      throw new IllegalArgumentException("The input point must lie in its declared unit domain.");
    }
  }
}
