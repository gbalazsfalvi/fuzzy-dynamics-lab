package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Deterministic tolerance, arithmetic, refinement limits, and selection settings.
 *
 * @param arithmetic the directed interval policy
 * @param tolerance the requested bound in (0,1), avoiding the trivial bound one
 * @param maxRefinements the maximum completed splits
 * @param maxCells the maximum active leaves
 * @param maxDepth the maximum binary path depth
 * @param fairnessPeriod every nth adaptive split selects the oldest eligible leaf
 * @param strategy the deterministic selection policy
 */
public record CaeSettings(
    DecimalArithmetic arithmetic,
    BigDecimal tolerance,
    int maxRefinements,
    int maxCells,
    int maxDepth,
    int fairnessPeriod,
    Strategy strategy) {
  /**
   * Validates tolerance and resource settings.
   *
   * @param arithmetic the rounding policy
   * @param tolerance the tolerance in (0,1)
   * @param maxRefinements the nonnegative split budget
   * @param maxCells the positive leaf budget
   * @param maxDepth the nonnegative depth budget
   * @param fairnessPeriod the positive fairness period
   * @param strategy the selection policy
   * @throws IllegalArgumentException if any numeric setting is outside its supported range
   */
  public CaeSettings {
    Objects.requireNonNull(arithmetic, "arithmetic");
    tolerance = Objects.requireNonNull(tolerance, "tolerance").stripTrailingZeros();
    Objects.requireNonNull(strategy, "strategy");
    if (tolerance.signum() <= 0
        || tolerance.compareTo(BigDecimal.ONE) >= 0
        || maxRefinements < 0
        || maxCells < 1
        || maxDepth < 0
        || fairnessPeriod < 1) {
      throw new IllegalArgumentException("CAE needs tolerance in (0,1) and valid finite budgets.");
    }
  }

  /**
   * Creates modest local defaults.
   *
   * @param tolerance the requested exact decimal tolerance
   * @return 34 digit arithmetic, up to 256 splits and 257 cells
   */
  public static CaeSettings standard(BigDecimal tolerance) {
    return new CaeSettings(
        new DecimalArithmetic(34), tolerance, 256, 257, 24, 5, Strategy.MAXIMUM_BOUND);
  }

  /** Deterministic policies sharing the same certificate and fairness engine. */
  public enum Strategy {
    /** Select the largest horizon bound, with age breaking ties and periodic FIFO fairness. */
    MAXIMUM_BOUND,
    /** Always select the oldest eligible leaf. */
    UNIFORM,
    /** Rank only the first transition (or time zero for a zero horizon). */
    NEXT_STEP_BOUND,
    /** Rank a heuristic predicted horizon reduction per declared work unit. */
    PREDICTED_GAIN_PER_WORK,
    /** Use an explicitly supplied deterministic ranker; the certificate remains unchanged. */
    RANKED
  }
}
