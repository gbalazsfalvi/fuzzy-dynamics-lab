package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A trajectory enclosure with the terms needed to audit its spatial error radius.
 *
 * @param time the nonnegative time
 * @param reachable the tightened image enclosure
 * @param representative the finite decimal representative
 * @param radius the final spatial radius
 * @param directRadius the radius from interval image propagation before tightening
 * @param propagatedRadius the Lipschitz recurrence radius
 * @param transitionLipschitz the bound used for the incoming transition, zero initially
 * @param evaluationResidual the representative evaluation error, zero initially
 */
public record CaeStep(
    int time,
    Box reachable,
    DecimalPoint representative,
    BigDecimal radius,
    BigDecimal directRadius,
    BigDecimal propagatedRadius,
    BigDecimal transitionLipschitz,
    BigDecimal evaluationResidual) {
  /**
   * Checks dimensions and nonnegative values, without trusting their mathematical provenance.
   *
   * @param time the time
   * @param reachable the enclosure in the unit domain
   * @param representative the representative in the same unit domain
   * @param radius the final radius
   * @param directRadius the direct radius
   * @param propagatedRadius the recurrence radius
   * @param transitionLipschitz the incoming Lipschitz bound
   * @param evaluationResidual the incoming residual
   * @throws IllegalArgumentException if shape or range checks fail
   */
  public CaeStep {
    Objects.requireNonNull(reachable, "reachable");
    Objects.requireNonNull(representative, "representative");
    if (time < 0 || reachable.dimension() != representative.dimension()) {
      throw new IllegalArgumentException("CAE trajectory dimensions and times must agree.");
    }
    UnitDomain.require(reachable, reachable.dimension());
    UnitDomain.require(representative.toExact(), reachable.dimension());
    radius = CaeMath.nonnegative(radius);
    directRadius = CaeMath.nonnegative(directRadius);
    propagatedRadius = CaeMath.nonnegative(propagatedRadius);
    transitionLipschitz = CaeMath.nonnegative(transitionLipschitz);
    evaluationResidual = CaeMath.nonnegative(evaluationResidual);
  }
}
