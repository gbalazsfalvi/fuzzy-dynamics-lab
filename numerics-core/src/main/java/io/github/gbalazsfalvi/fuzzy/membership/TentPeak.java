package io.github.gbalazsfalvi.fuzzy.membership;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * The profile height * max(0, 1 - infinityDistance(x, center) / width).
 *
 * <p>The profile is restricted to the unit domain; its untruncated support may extend beyond the
 * boundary. Its global Lipschitz constant is at most height / width.
 *
 * @param center the exact center inside the unit domain
 * @param width the strictly positive exact width
 * @param height the exact peak height in [0,1]
 */
public record TentPeak(ExactPoint center, Rational width, Rational height)
    implements CertifiedMembership {
  /**
   * Validates the peak parameters.
   *
   * @param center the center in the unit domain
   * @param width the positive width
   * @param height the height in [0,1]
   * @throws IllegalArgumentException if parameters violate these constraints
   */
  public TentPeak {
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(width, "width");
    Objects.requireNonNull(height, "height");
    UnitDomain.require(center, center.dimension());
    if (width.compareTo(Rational.ZERO) <= 0
        || height.compareTo(Rational.ZERO) < 0
        || height.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("A peak needs positive width and height in [0,1].");
    }
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return center.dimension();
  }

  /** {@inheritDoc} */
  @Override
  public DecimalInterval range(Box input, DecimalArithmetic arithmetic) {
    UnitDomain.require(input, dimension());
    DecimalInterval distance = arithmetic.enclose(Rational.ZERO);
    for (int axis = 0; axis < dimension(); axis++) {
      DecimalInterval displacement =
          arithmetic.subtract(input.axis(axis), arithmetic.enclose(center.coordinate(axis)));
      distance = arithmetic.maximum(distance, arithmetic.abs(displacement));
    }
    DecimalInterval shape =
        arithmetic.maximum(
            arithmetic.enclose(Rational.ZERO),
            arithmetic.subtract(
                arithmetic.enclose(Rational.ONE),
                arithmetic.divide(distance, arithmetic.enclose(width))));
    return arithmetic
        .multiply(arithmetic.enclose(height), shape)
        .intersection(DecimalInterval.of("0", "1"))
        .orElseThrow(
            () -> new IllegalStateException("A membership range lost its unit invariant."));
  }

  /** {@inheritDoc} */
  @Override
  public Rational evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    return height.multiply(
        Rational.ONE.subtract(input.distanceInfinity(center).divide(width)).max(Rational.ZERO));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(DecimalArithmetic arithmetic) {
    return arithmetic.enclose(height.divide(width)).upper();
  }
}
