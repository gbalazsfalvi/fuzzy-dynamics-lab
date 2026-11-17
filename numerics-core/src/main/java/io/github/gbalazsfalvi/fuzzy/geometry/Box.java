package io.github.gbalazsfalvi.fuzzy.geometry;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An immutable nonempty Cartesian product of closed decimal intervals.
 *
 * @param axes the ordered coordinate intervals
 */
public record Box(List<DecimalInterval> axes) {
  /**
   * Copies the coordinate intervals.
   *
   * @param axes the nonempty interval list
   * @throws IllegalArgumentException if the dimension is zero
   */
  public Box {
    axes = List.copyOf(axes);
    if (axes.isEmpty()) {
      throw new IllegalArgumentException("A box must have at least one axis.");
    }
  }

  /**
   * Creates a box from ordered intervals.
   *
   * @param axes the coordinate intervals
   * @return the box
   */
  public static Box of(DecimalInterval... axes) {
    return new Box(List.of(axes));
  }

  /**
   * Creates a normalized unit box.
   *
   * @param dimension the positive coordinate count
   * @return the unit box
   * @throws IllegalArgumentException if the dimension is not positive
   */
  public static Box unit(int dimension) {
    if (dimension < 1) {
      throw new IllegalArgumentException("A unit box dimension must be positive.");
    }
    return new Box(Collections.nCopies(dimension, DecimalInterval.of("0", "1")));
  }

  /**
   * Creates a singleton box.
   *
   * @param point the exact decimal point
   * @return its singleton box
   */
  public static Box point(DecimalPoint point) {
    return new Box(point.coordinates().stream().map(DecimalInterval::point).toList());
  }

  /**
   * Returns the coordinate count.
   *
   * @return the positive dimension
   */
  public int dimension() {
    return axes.size();
  }

  /**
   * Returns a coordinate interval.
   *
   * @param axis the zero based axis
   * @return the interval
   */
  public DecimalInterval axis(int axis) {
    return axes.get(axis);
  }

  /**
   * Tests exact decimal point containment.
   *
   * @param point a point of the same dimension
   * @return whether all coordinates are enclosed
   */
  public boolean contains(DecimalPoint point) {
    return contains(point.toExact());
  }

  /**
   * Tests rational point containment without rounding the reference.
   *
   * @param point a point of the same dimension
   * @return whether all coordinates are enclosed
   * @throws IllegalArgumentException if dimensions differ
   */
  public boolean contains(ExactPoint point) {
    requireDimension(point.dimension());
    for (int axis = 0; axis < dimension(); axis++) {
      if (!axis(axis).contains(point.coordinate(axis))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests enclosure of a complete box.
   *
   * @param other a box of the same dimension
   * @return whether every coordinate interval is enclosed
   * @throws IllegalArgumentException if dimensions differ
   */
  public boolean contains(Box other) {
    requireDimension(other.dimension());
    for (int axis = 0; axis < dimension(); axis++) {
      if (!axis(axis).contains(other.axis(axis))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Intersects boxes without replacing a disjoint result by a fabricated interval.
   *
   * @param other a box of the same dimension
   * @return their intersection, if nonempty
   * @throws IllegalArgumentException if dimensions differ
   */
  public Optional<Box> intersection(Box other) {
    requireDimension(other.dimension());
    List<DecimalInterval> result = new ArrayList<>();
    for (int axis = 0; axis < dimension(); axis++) {
      Optional<DecimalInterval> overlap = axis(axis).intersection(other.axis(axis));
      if (overlap.isEmpty()) {
        return Optional.empty();
      }
      result.add(overlap.orElseThrow());
    }
    return Optional.of(new Box(result));
  }

  /**
   * Computes the smallest Cartesian box containing both boxes.
   *
   * @param other the equally dimensioned box
   * @return the coordinatewise hull
   * @throws IllegalArgumentException if dimensions differ
   */
  public Box hull(Box other) {
    requireDimension(other.dimension());
    List<DecimalInterval> result = new ArrayList<>();
    for (int axis = 0; axis < dimension(); axis++) {
      result.add(axis(axis).hull(other.axis(axis)));
    }
    return new Box(result);
  }

  /**
   * Creates an infinity norm ball using exact decimal endpoint arithmetic.
   *
   * @param center the center
   * @param radius the nonnegative radius
   * @return the box, which may extend outside the unit domain
   * @throws IllegalArgumentException if the radius is negative
   */
  public static Box ball(DecimalPoint center, BigDecimal radius) {
    if (radius.signum() < 0) {
      throw new IllegalArgumentException("A ball radius must be nonnegative.");
    }
    return new Box(
        center.coordinates().stream()
            .map(value -> new DecimalInterval(value.subtract(radius), value.add(radius)))
            .toList());
  }

  /**
   * Computes an exact decimal midpoint.
   *
   * @return a point contained in the box
   */
  public DecimalPoint midpoint() {
    return new DecimalPoint(axes.stream().map(DecimalInterval::midpoint).toList());
  }

  /**
   * Bounds all infinity norm distances from a chosen point.
   *
   * @param point the reference point of the same dimension
   * @param arithmetic the upward rounding policy
   * @return an upper radius containing the whole box
   */
  public BigDecimal radiusAround(DecimalPoint point, DecimalArithmetic arithmetic) {
    requireDimension(point.dimension());
    BigDecimal radius = BigDecimal.ZERO;
    for (int axis = 0; axis < dimension(); axis++) {
      BigDecimal coordinate = point.coordinate(axis);
      radius =
          radius
              .max(axis(axis).lower().subtract(coordinate).abs())
              .max(axis(axis).upper().subtract(coordinate).abs());
    }
    return radius.round(arithmetic.upward());
  }

  /**
   * Bisects a longest side into two closed children with a shared boundary.
   *
   * @return two boxes whose union equals this box
   * @throws IllegalStateException if the box is a singleton
   */
  public List<Box> bisectLongest() {
    return bisectLongest(0);
  }

  /**
   * Bisects a longest side with a prescribed first axis for deterministic tie breaking.
   *
   * @param preferredAxis the first axis considered when widths tie
   * @return two closed children sharing their exact midpoint boundary
   * @throws IllegalArgumentException if the preferred axis is outside the dimension
   * @throws IllegalStateException if the box is a singleton
   */
  public List<Box> bisectLongest(int preferredAxis) {
    if (preferredAxis < 0 || preferredAxis >= dimension()) {
      throw new IllegalArgumentException("The preferred axis must belong to the box.");
    }
    int selected = preferredAxis;
    BigDecimal maximum = BigDecimal.ZERO;
    for (int offset = 0; offset < dimension(); offset++) {
      int axis = (preferredAxis + offset) % dimension();
      BigDecimal width = axis(axis).upper().subtract(axis(axis).lower());
      if (width.compareTo(maximum) > 0) {
        maximum = width;
        selected = axis;
      }
    }
    if (maximum.signum() == 0) {
      throw new IllegalStateException("A singleton box cannot be bisected.");
    }
    DecimalInterval interval = axis(selected);
    BigDecimal midpoint = interval.midpoint();
    List<DecimalInterval> left = new ArrayList<>(axes);
    List<DecimalInterval> right = new ArrayList<>(axes);
    left.set(selected, new DecimalInterval(interval.lower(), midpoint));
    right.set(selected, new DecimalInterval(midpoint, interval.upper()));
    return List.of(new Box(left), new Box(right));
  }

  private void requireDimension(int other) {
    if (dimension() != other) {
      throw new IllegalArgumentException("Geometry dimensions must agree.");
    }
  }
}
