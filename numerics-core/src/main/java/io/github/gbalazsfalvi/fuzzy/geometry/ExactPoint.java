package io.github.gbalazsfalvi.fuzzy.geometry;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable rational point for exact references.
 *
 * @param coordinates the nonempty ordered coordinate list
 */
public record ExactPoint(List<Rational> coordinates) implements Comparable<ExactPoint> {
  /**
   * Defensively copies the coordinates.
   *
   * @param coordinates the nonempty coordinate list
   * @throws IllegalArgumentException if there are no coordinates
   */
  public ExactPoint {
    coordinates = List.copyOf(coordinates);
    if (coordinates.isEmpty()) {
      throw new IllegalArgumentException("A point must have at least one coordinate.");
    }
  }

  /**
   * Parses exact rational coordinates.
   *
   * @param values the textual coordinates
   * @return the point
   */
  public static ExactPoint of(String... values) {
    return new ExactPoint(Arrays.stream(values).map(Rational::parse).toList());
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
   * @return the exact coordinate
   */
  public Rational coordinate(int axis) {
    return coordinates.get(axis);
  }

  /**
   * Computes an exact infinity norm distance.
   *
   * @param other a point of the same dimension
   * @return the distance
   * @throws IllegalArgumentException if dimensions differ
   */
  public Rational distanceInfinity(ExactPoint other) {
    if (dimension() != other.dimension()) {
      throw new IllegalArgumentException("Point dimensions must agree.");
    }
    Rational maximum = Rational.ZERO;
    for (int axis = 0; axis < dimension(); axis++) {
      maximum = maximum.max(coordinate(axis).subtract(other.coordinate(axis)).abs());
    }
    return maximum;
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(ExactPoint other) {
    int dimensions = Integer.compare(dimension(), other.dimension());
    if (dimensions != 0) {
      return dimensions;
    }
    for (int axis = 0; axis < dimension(); axis++) {
      int order = coordinate(axis).compareTo(other.coordinate(axis));
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }
}
