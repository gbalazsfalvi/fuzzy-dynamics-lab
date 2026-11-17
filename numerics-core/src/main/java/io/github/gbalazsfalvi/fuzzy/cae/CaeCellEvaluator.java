package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Canonical interval and Lipschitz recipe shared by generation and strict certificate replay. */
final class CaeCellEvaluator {
  private CaeCellEvaluator() {}

  static CaeCell evaluate(
      CaeProblem problem, CaeSettings settings, CellPath path, CaeControl control) {
    control.cell();
    DecimalArithmetic arithmetic = settings.arithmetic();
    Box initial = path.box(problem.map().dimension());
    DecimalPoint representative = initial.midpoint();
    BigDecimal radius = initial.radiusAround(representative, arithmetic);
    DecimalInterval whole = control.range(problem, initial, arithmetic);
    DecimalInterval atPoint = control.range(problem, Box.point(representative), arithmetic);
    MembershipSample sampled = MembershipSample.fromRanges(whole, atPoint, arithmetic);
    BigDecimal lipschitzVariation =
        control.membershipLipschitz(problem, arithmetic).multiply(radius, arithmetic.upward());
    MembershipSample membership =
        new MembershipSample(
            sampled.grade(),
            sampled.gradeError(),
            sampled.variation().min(lipschitzVariation),
            sampled.upperMembership());
    List<CaeStep> trajectory = new ArrayList<>();
    trajectory.add(
        new CaeStep(
            0, initial, representative, radius, radius, radius, BigDecimal.ZERO, BigDecimal.ZERO));
    Box reachable = initial;
    for (int time = 1; time <= problem.horizon(); time++) {
      control.check();
      // Include the numerical representative even when rounding places it outside the tight box.
      Box comparisonRegion = reachable.hull(Box.point(representative));
      BigDecimal lipschitz = control.lipschitz(problem, comparisonRegion, arithmetic);
      Box pointImage = control.image(problem, Box.point(representative), arithmetic);
      DecimalPoint next = pointImage.midpoint();
      BigDecimal residual = pointImage.radiusAround(next, arithmetic);
      BigDecimal propagated =
          lipschitz.multiply(radius, arithmetic.upward()).add(residual, arithmetic.upward());
      Box image = control.image(problem, reachable, arithmetic);
      BigDecimal direct = image.radiusAround(next, arithmetic);
      BigDecimal candidate = direct.min(propagated);
      // Both enclosures contain the exact image. Their intersection therefore retains coverage.
      reachable =
          image
              .intersection(Box.ball(next, candidate))
              .orElseThrow(
                  () -> new ArithmeticException("Verified trajectory enclosures became disjoint."));
      radius = candidate.min(reachable.radiusAround(next, arithmetic));
      representative = next;
      trajectory.add(
          new CaeStep(time, reachable, next, radius, direct, propagated, lipschitz, residual));
    }
    List<BigDecimal> bounds =
        trajectory.stream()
            .map(step -> CellBounds.endograph(membership, step.radius(), arithmetic))
            .toList();
    return new CaeCell(path, initial, membership, trajectory, bounds);
  }
}
