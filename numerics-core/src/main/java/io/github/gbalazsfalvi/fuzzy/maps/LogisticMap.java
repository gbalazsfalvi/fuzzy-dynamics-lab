package io.github.gbalazsfalvi.fuzzy.maps;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The map f(x) = parameter * x * (1 - x), including its interior extremum.
 *
 * @param parameter the exact parameter in [0,4]
 */
public record LogisticMap(Rational parameter) implements CertifiedMap {
  /**
   * Validates unit domain invariance.
   *
   * @param parameter the value in [0,4]
   * @throws IllegalArgumentException if the parameter is outside [0,4]
   */
  public LogisticMap {
    Objects.requireNonNull(parameter, "parameter");
    if (parameter.compareTo(Rational.ZERO) < 0 || parameter.compareTo(Rational.of(4)) > 0) {
      throw new IllegalArgumentException("The logistic parameter must lie in [0,4].");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return "logistic(" + parameter + ")";
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
    // Completing the square preserves the turning point and avoids x*(1-x) dependency inflation.
    DecimalInterval shifted =
        arithmetic.subtract(input.axis(0), arithmetic.enclose(Rational.of(1, 2)));
    DecimalInterval parabola =
        arithmetic.subtract(arithmetic.enclose(Rational.of(1, 4)), arithmetic.square(shifted));
    return MapSupport.invariantImage(arithmetic.multiply(arithmetic.enclose(parameter), parabola));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(Box region, DecimalArithmetic arithmetic) {
    UnitDomain.require(region, dimension());
    DecimalInterval derivative =
        arithmetic.subtract(
            arithmetic.enclose(Rational.ONE),
            arithmetic.multiply(arithmetic.enclose(Rational.TWO), region.axis(0)));
    return arithmetic.multiply(arithmetic.enclose(parameter), arithmetic.abs(derivative)).upper();
  }

  /** {@inheritDoc} */
  @Override
  public ExactPoint evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    Rational x = input.coordinate(0);
    return new ExactPoint(List.of(parameter.multiply(x).multiply(Rational.ONE.subtract(x))));
  }
}
