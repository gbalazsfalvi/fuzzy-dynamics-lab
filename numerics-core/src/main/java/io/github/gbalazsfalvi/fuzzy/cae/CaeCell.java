package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Immutable evidence for one initial cell and its complete finite horizon.
 *
 * @param path the canonical binary cell identity
 * @param initial the complete initial box
 * @param membership the initial representative grade and variation quantities
 * @param trajectory the enclosures at times zero through the horizon
 * @param bounds the P1 endograph bound contributions at those times
 */
public record CaeCell(
    CellPath path,
    Box initial,
    MembershipSample membership,
    List<CaeStep> trajectory,
    List<BigDecimal> bounds) {
  /**
   * Copies and canonicalizes the evidence; a verifier establishes its provenance.
   *
   * @param path the binary identity
   * @param initial the initial box
   * @param membership the membership quantities
   * @param trajectory the complete trajectory
   * @param bounds the corresponding contributions in [0,1]
   * @throws IllegalArgumentException if sequence lengths, dimensions, or times disagree
   */
  public CaeCell {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(initial, "initial");
    Objects.requireNonNull(membership, "membership");
    membership =
        new MembershipSample(
            membership.grade().stripTrailingZeros(), membership.gradeError().stripTrailingZeros(),
            membership.variation().stripTrailingZeros(),
                membership.upperMembership().stripTrailingZeros());
    trajectory = List.copyOf(trajectory);
    bounds = CaeMath.bounds(bounds);
    if (trajectory.size() != bounds.size()) {
      throw new IllegalArgumentException("A cell needs one bound per trajectory time.");
    }
    for (int time = 0; time < trajectory.size(); time++) {
      if (trajectory.get(time).time() != time
          || trajectory.get(time).reachable().dimension() != initial.dimension()) {
        throw new IllegalArgumentException(
            "A cell trajectory must have consecutive, equally dimensioned times.");
      }
    }
  }

  /**
   * Returns the cell's largest contribution over the whole horizon.
   *
   * @return the maximum bound
   */
  public BigDecimal worstBound() {
    return CaeMath.maximum(bounds);
  }
}
