package io.github.gbalazsfalvi.fuzzy.pwl;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import java.util.List;

/**
 * An exact continuous piecewise linear unit self map, explicitly independent of CAE box evaluation.
 *
 * @param knots the strictly increasing domain vertices, from zero through one
 */
public record ExactLinearMap(List<LinearKnot> knots) {
  /**
   * Checks full domain coverage, continuity through shared vertices, and unit range invariance.
   *
   * @param knots the vertices in increasing coordinate order
   * @throws IllegalArgumentException if endpoints, order, or cardinality are invalid
   */
  public ExactLinearMap {
    knots = List.copyOf(knots);
    if (knots.size() < 2
        || !knots.getFirst().coordinate().equals(Rational.ZERO)
        || !knots.getLast().coordinate().equals(Rational.ONE)) {
      throw new IllegalArgumentException("A linear map must cover the complete unit interval.");
    }
    for (int index = 1; index < knots.size(); index++) {
      if (knots.get(index - 1).coordinate().compareTo(knots.get(index).coordinate()) >= 0) {
        throw new IllegalArgumentException("Linear map coordinates must increase strictly.");
      }
    }
  }

  /**
   * Provides exact built in affine and tent maps; nonlinear models require separate preprocessing.
   *
   * @param map the supported built in scalar map
   * @return an exact piecewise linear equivalent
   * @throws IllegalArgumentException if the map is nonlinear or not scalar
   */
  public static ExactLinearMap from(CertifiedMap map) {
    if (map instanceof AffineContraction affine) {
      Rational offset = Rational.ONE.subtract(affine.factor()).divide(Rational.TWO);
      return new ExactLinearMap(
          List.of(
              new LinearKnot(Rational.ZERO, offset),
              new LinearKnot(Rational.ONE, affine.factor().add(offset))));
    }
    if (map instanceof TentMap) {
      return new ExactLinearMap(
          List.of(
              new LinearKnot(Rational.ZERO, Rational.ZERO),
              new LinearKnot(Rational.of(1, 2), Rational.ONE),
              new LinearKnot(Rational.ONE, Rational.ZERO)));
    }
    throw new IllegalArgumentException("The exact PWL baseline accepts affine and tent maps only.");
  }

  /**
   * Evaluates the exact linear interpolant.
   *
   * @param coordinate the coordinate in [0,1]
   * @return the exact image
   * @throws IllegalArgumentException if the input is outside the unit domain
   */
  public Rational at(Rational coordinate) {
    if (coordinate.compareTo(Rational.ZERO) < 0 || coordinate.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Linear map input must belong to [0,1].");
    }
    for (int index = 1; index < knots.size(); index++) {
      LinearKnot left = knots.get(index - 1);
      LinearKnot right = knots.get(index);
      if (coordinate.compareTo(right.coordinate()) <= 0) {
        return new LinearSegment(
                new RationalInterval(left.coordinate(), right.coordinate()),
                left.value(),
                right.value())
            .at(coordinate);
      }
    }
    throw new AssertionError("Validated full map coverage was lost.");
  }
}
