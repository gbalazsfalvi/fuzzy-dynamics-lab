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
 * A convex coupling of two tent maps on the unit square.
 *
 * <p>Each row is a convex combination, so the square is invariant and the infinity norm Lipschitz
 * constant is at most two for every allowed coupling.
 *
 * @param coupling the exact mixing weight in [0,1]
 */
public record CoupledTentMap(Rational coupling) implements CertifiedMap {
  /**
   * Validates the convex weight.
   *
   * @param coupling the exact weight in [0,1]
   * @throws IllegalArgumentException if the weight is outside [0,1]
   */
  public CoupledTentMap {
    Objects.requireNonNull(coupling, "coupling");
    if (coupling.compareTo(Rational.ZERO) < 0 || coupling.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("The coupling must lie in [0,1].");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return "coupled-tent(" + coupling + ")";
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return 2;
  }

  /** {@inheritDoc} */
  @Override
  public Box image(Box input, DecimalArithmetic arithmetic) {
    UnitDomain.require(input, dimension());
    DecimalInterval x = MapSupport.tentImage(input.axis(0), arithmetic);
    DecimalInterval y = MapSupport.tentImage(input.axis(1), arithmetic);
    DecimalInterval mixed = arithmetic.enclose(coupling);
    DecimalInterval retained = arithmetic.enclose(Rational.ONE.subtract(coupling));
    return MapSupport.invariantImage(
        arithmetic.add(arithmetic.multiply(retained, x), arithmetic.multiply(mixed, y)),
        arithmetic.add(arithmetic.multiply(mixed, x), arithmetic.multiply(retained, y)));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(Box region, DecimalArithmetic arithmetic) {
    UnitDomain.require(region, dimension());
    return BigDecimal.TWO;
  }

  /** {@inheritDoc} */
  @Override
  public ExactPoint evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    Rational x = MapSupport.tent(input.coordinate(0));
    Rational y = MapSupport.tent(input.coordinate(1));
    Rational retained = Rational.ONE.subtract(coupling);
    return new ExactPoint(
        List.of(
            retained.multiply(x).add(coupling.multiply(y)),
            coupling.multiply(x).add(retained.multiply(y))));
  }
}
