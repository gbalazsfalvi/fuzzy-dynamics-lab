package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;

/** The P1 cell contribution in the declared max product endograph metric. */
public final class CellBounds {
  private CellBounds() {}

  /**
   * Combines verified spatial and vertical quantities with the common baseline grade cap.
   *
   * <p>Validity requires independently established membership and trajectory enclosures. Supplying
   * invented nonnegative numbers does not make them a certificate.
   *
   * @param membership the initial cell quantities
   * @param spatialRadius the verified nonnegative spatial radius
   * @param arithmetic the upward rounding policy
   * @return min(max(M,a), max(radius, kappa+sigma)) in [0,1]
   * @throws IllegalArgumentException if the radius is negative
   */
  public static BigDecimal endograph(
      MembershipSample membership, BigDecimal spatialRadius, DecimalArithmetic arithmetic) {
    spatialRadius = CaeMath.nonnegative(spatialRadius);
    BigDecimal vertical = membership.variation().add(membership.gradeError(), arithmetic.upward());
    BigDecimal cap = membership.upperMembership().max(membership.grade());
    return cap.min(spatialRadius.max(vertical)).stripTrailingZeros();
  }
}
