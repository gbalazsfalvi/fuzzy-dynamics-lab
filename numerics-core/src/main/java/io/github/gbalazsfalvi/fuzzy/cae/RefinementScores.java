package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;

/**
 * Deterministic policy heuristics. Scores select cells; they never enter a certificate.
 *
 * <p>The predicted gain model halves spatial radii and initial membership variation, retains the
 * grade error and cap, and divides the predicted reduction by two child evaluation costs plus path
 * reconstruction. This is a ranking model, not a valid bound on the actual children.
 */
final class RefinementScores {
  private RefinementScores() {}

  static BigDecimal score(CaeCell cell, CaeSettings settings) {
    return switch (settings.strategy()) {
      case MAXIMUM_BOUND, UNIFORM -> cell.worstBound();
      case NEXT_STEP_BOUND -> cell.bounds().get(Math.min(1, cell.bounds().size() - 1));
      case PREDICTED_GAIN_PER_WORK -> predictedGain(cell, settings.arithmetic());
      case RANKED ->
          throw new IllegalStateException("An external ranker must supply its own score.");
    };
  }

  private static BigDecimal predictedGain(CaeCell cell, DecimalArithmetic arithmetic) {
    MembershipSample membership = cell.membership();
    MembershipSample predicted =
        new MembershipSample(
            membership.grade(),
            membership.gradeError(),
            membership.variation().divide(BigDecimal.TWO, arithmetic.upward()),
            membership.upperMembership());
    BigDecimal predictedBound = BigDecimal.ZERO;
    for (CaeStep step : cell.trajectory()) {
      BigDecimal radius = step.radius().divide(BigDecimal.TWO, arithmetic.upward());
      predictedBound = predictedBound.max(CellBounds.endograph(predicted, radius, arithmetic));
    }
    BigDecimal gain = cell.worstBound().subtract(predictedBound).max(BigDecimal.ZERO);
    // Each child uses 2T images, T spatial bounds, two ranges, and one membership bound.
    // Path reconstruction is represented by one unit per traversed ancestor per child.
    long workUnits = 2L * (3L * cell.trajectory().size() + cell.path().depth() + 1);
    return gain.divide(BigDecimal.valueOf(workUnits), arithmetic.upward());
  }
}
