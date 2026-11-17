package io.github.gbalazsfalvi.fuzzy.maps;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The map f(x) = factor * x + (1 - factor) / 2 with a fixed point at one half.
 *
 * @param factor the exact contraction factor in [0,1)
 */
public record AffineContraction(Rational factor) implements CertifiedMap {
  /**
   * Validates the exact contraction factor.
   *
   * @param factor the value in [0,1)
   * @throws IllegalArgumentException if the parameter is outside [0,1)
   */
  public AffineContraction {
    Objects.requireNonNull(factor, "factor");
    if (factor.compareTo(Rational.ZERO) < 0 || factor.compareTo(Rational.ONE) >= 0) {
      throw new IllegalArgumentException("The contraction factor must lie in [0,1).");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return "affine(" + factor + ")";
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public Box image(Box input, DecimalArithmetic arithmetic) {
    UnitDomain.require(input, dimension());
    return MapSupport.invariantImage(
        arithmetic.add(
            arithmetic.multiply(arithmetic.enclose(factor), input.axis(0)),
            arithmetic.enclose(offset())));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(Box region, DecimalArithmetic arithmetic) {
    UnitDomain.require(region, dimension());
    return arithmetic.enclose(factor).upper();
  }

  /** {@inheritDoc} */
  @Override
  public ExactPoint evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    return new ExactPoint(List.of(factor.multiply(input.coordinate(0)).add(offset())));
  }

  private Rational offset() {
    return Rational.ONE.subtract(factor).divide(Rational.TWO);
  }
}
