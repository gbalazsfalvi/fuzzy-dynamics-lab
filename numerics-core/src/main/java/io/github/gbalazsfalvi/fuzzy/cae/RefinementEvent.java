package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One committed refinement and the retained best horizon bound.
 *
 * @param refinement the positive completed split count
 * @param parent the replaced leaf
 * @param selection the reason for selecting it
 * @param currentBound the new partition's worst bound
 * @param bestBound the retained incumbent's worst bound
 * @param activeCells the active leaf count after the split
 */
public record RefinementEvent(
    int refinement,
    CellPath parent,
    Selection selection,
    BigDecimal currentBound,
    BigDecimal bestBound,
    int activeCells) {
  /**
   * Validates the event shape and incumbent ordering.
   *
   * @param refinement the positive split index
   * @param parent the parent path
   * @param selection the selection reason
   * @param currentBound the new worst bound in [0,1]
   * @param bestBound the retained bound in [0,1]
   * @param activeCells the positive active leaf count
   * @throws IllegalArgumentException if counts or bounds are inconsistent
   */
  public RefinementEvent {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(selection, "selection");
    currentBound = CaeMath.nonnegative(currentBound);
    bestBound = CaeMath.nonnegative(bestBound);
    if (refinement < 1
        || activeCells < 1
        || currentBound.compareTo(BigDecimal.ONE) > 0
        || bestBound.compareTo(currentBound) > 0) {
      throw new IllegalArgumentException(
          "Refinement event counts and incumbent bounds must agree.");
    }
  }

  /** How a completed refinement was selected. */
  public enum Selection {
    /** Largest horizon contribution, with creation order breaking ties. */
    MAXIMUM_BOUND,
    /** Periodic oldest eligible leaf selection. */
    FAIRNESS,
    /** Unconditional oldest eligible leaf selection. */
    UNIFORM,
    /** Largest contribution at the first transition only. */
    NEXT_STEP_BOUND,
    /** Heuristic predicted certificate reduction divided by estimated work. */
    PREDICTED_GAIN_PER_WORK,
    /** An explicitly supplied ranker chose this cell, with the same FIFO fairness rule. */
    RANKED
  }
}
