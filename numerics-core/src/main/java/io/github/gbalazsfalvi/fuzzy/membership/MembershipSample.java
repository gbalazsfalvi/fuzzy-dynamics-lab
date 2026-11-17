package io.github.gbalazsfalvi.fuzzy.membership;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import java.math.BigDecimal;

/**
 * Verified membership quantities for a representative and its complete initial cell.
 *
 * @param grade a stored lower bound on the representative's exact membership
 * @param gradeError an upper bound on its evaluation error
 * @param variation an upper bound on absolute membership variation relative to the representative
 * @param upperMembership an upper bound on every membership in the cell
 */
public record MembershipSample(
    BigDecimal grade, BigDecimal gradeError, BigDecimal variation, BigDecimal upperMembership) {
  /**
   * Checks basic range consistency.
   *
   * @param grade the stored grade in [0,1]
   * @param gradeError the nonnegative grade error
   * @param variation the nonnegative variation bound
   * @param upperMembership the cell upper membership in [0,1]
   * @throws IllegalArgumentException if the stored quantities are inconsistent
   */
  public MembershipSample {
    if (grade.signum() < 0
        || upperMembership.compareTo(BigDecimal.ONE) > 0
        || grade.compareTo(upperMembership) > 0
        || gradeError.signum() < 0
        || variation.signum() < 0) {
      throw new IllegalArgumentException(
          "Membership sample bounds must be nonnegative and consistent.");
    }
  }

  /**
   * Derives bounds using whole cell and singleton interval evaluations.
   *
   * @param membership the supported profile
   * @param cell the whole initial cell inside the unit domain
   * @param representative a point contained in that cell
   * @param arithmetic the directed rounding policy
   * @return a bounded membership sample, not a complete fuzzy trajectory certificate
   * @throws IllegalArgumentException if the representative is outside the cell
   */
  public static MembershipSample enclose(
      CertifiedMembership membership,
      Box cell,
      DecimalPoint representative,
      DecimalArithmetic arithmetic) {
    if (!cell.contains(representative)) {
      throw new IllegalArgumentException("The representative must belong to its initial cell.");
    }
    DecimalInterval whole = membership.range(cell, arithmetic);
    DecimalInterval point = membership.range(Box.point(representative), arithmetic);
    return fromRanges(whole, point, arithmetic);
  }

  /**
   * Derives sample quantities from verified whole cell and representative ranges.
   *
   * <p>The caller must establish that these ranges concern a representative inside that cell. This
   * operation combines supplied enclosures; it does not validate an arbitrary profile.
   *
   * @param whole the verified membership range of the complete cell
   * @param point the verified range at its representative
   * @param arithmetic the upward rounding policy
   * @return the stored grade and its error and variation bounds
   * @throws IllegalArgumentException if ranges are disjoint or outside [0,1]
   */
  public static MembershipSample fromRanges(
      DecimalInterval whole, DecimalInterval point, DecimalArithmetic arithmetic) {
    DecimalInterval unit = DecimalInterval.of("0", "1");
    if (!unit.contains(whole) || !unit.contains(point) || whole.intersection(point).isEmpty()) {
      throw new IllegalArgumentException("Membership ranges must overlap inside [0,1].");
    }
    BigDecimal variation =
        whole
            .upper()
            .subtract(point.lower(), arithmetic.upward())
            .max(point.upper().subtract(whole.lower(), arithmetic.upward()));
    return new MembershipSample(point.lower(), arithmetic.width(point), variation, whole.upper());
  }
}
