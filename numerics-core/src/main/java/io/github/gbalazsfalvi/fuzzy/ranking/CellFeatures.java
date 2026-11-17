package io.github.gbalazsfalvi.fuzzy.ranking;

import io.github.gbalazsfalvi.fuzzy.cae.CaeCell;
import io.github.gbalazsfalvi.fuzzy.cae.CaeStep;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Versioned deterministic floating point descriptors used only for empirical selection.
 *
 * <p>Converting these descriptors to double never changes a certificate. No target label, trial
 * child result, test identity, or exact reference discrepancy is included in the features.
 */
public final class CellFeatures {
  /** Exact order and semantics of the exported feature vector. */
  public static final String VERSION = "cae-cell-features-v1";

  private static final List<String> NAMES =
      List.of(
          "dimension",
          "horizonLog",
          "depth",
          "initialRadius",
          "grade",
          "gradeError",
          "variation",
          "upperMembership",
          "horizonBound",
          "nextStepBound",
          "terminalBound",
          "maximumRadius",
          "meanRadius",
          "maximumLipschitz",
          "tighteningRatio",
          "widthRatio");

  private CellFeatures() {}

  /**
   * Returns the immutable model input column order.
   *
   * @return the ordered feature names
   */
  public static List<String> names() {
    return NAMES;
  }

  /**
   * Computes numerical descriptors without any trial refinement or reference computation.
   *
   * @param cell the complete verified cell
   * @return the finite descriptor vector in the declared order
   * @throws ArithmeticException if an unsupported value cannot be represented finitely
   */
  public static List<Double> of(CaeCell cell) {
    int horizon = cell.trajectory().size() - 1;
    CaeStep last = cell.trajectory().getLast();
    BigDecimal minimumWidth = BigDecimal.ONE;
    BigDecimal maximumWidth = BigDecimal.ZERO;
    for (var axis : cell.initial().axes()) {
      BigDecimal width = axis.upper().subtract(axis.lower());
      minimumWidth = minimumWidth.min(width);
      maximumWidth = maximumWidth.max(width);
    }
    double maximumRadius = 0;
    double radiusSum = 0;
    double maximumLipschitz = 0;
    for (CaeStep step : cell.trajectory()) {
      double radius = step.radius().doubleValue();
      maximumRadius = Math.max(maximumRadius, radius);
      radiusSum += radius;
      maximumLipschitz = Math.max(maximumLipschitz, step.transitionLipschitz().doubleValue());
    }
    double tightening =
        last.directRadius().signum() == 0
            ? 0
            : last.radius().divide(last.directRadius(), MathContext.DECIMAL64).doubleValue();
    double shape =
        maximumWidth.signum() == 0
            ? 0
            : minimumWidth.divide(maximumWidth, MathContext.DECIMAL64).doubleValue();
    List<Double> values =
        List.of(
            (double) cell.initial().dimension(),
            StrictMath.log1p(horizon),
            cell.path().depth() / 24.0,
            cell.trajectory().getFirst().radius().doubleValue(),
            cell.membership().grade().doubleValue(),
            cell.membership().gradeError().doubleValue(),
            cell.membership().variation().doubleValue(),
            cell.membership().upperMembership().doubleValue(),
            cell.worstBound().doubleValue(),
            cell.bounds().get(Math.min(1, horizon)).doubleValue(),
            cell.bounds().getLast().doubleValue(),
            maximumRadius,
            radiusSum / cell.trajectory().size(),
            maximumLipschitz,
            tightening,
            shape);
    if (values.stream().anyMatch(value -> !Double.isFinite(value))) {
      throw new ArithmeticException("Selection features must be finite.");
    }
    return values;
  }

  /**
   * Returns the shared nonlearned child evaluation and path reconstruction cost model.
   *
   * @param cell the candidate cell
   * @return positive work units used only for ranking
   */
  public static long estimatedChildWork(CaeCell cell) {
    return 2L * (3L * cell.trajectory().size() + cell.path().depth() + 1);
  }
}
