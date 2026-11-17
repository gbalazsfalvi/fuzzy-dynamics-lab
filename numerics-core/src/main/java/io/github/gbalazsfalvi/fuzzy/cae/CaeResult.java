package io.github.gbalazsfalvi.fuzzy.cae;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A solver outcome retaining both the best approximation and the final completed partition.
 *
 * @param status the outcome category
 * @param reason the stopping condition
 * @param detail the human readable explanation
 * @param best the earliest snapshot with the smallest achieved horizon bound
 * @param last the last complete snapshot, absent if initialization was interrupted
 * @param history the committed refinements, excluding discarded child computations
 * @param work the actual attempted numerical calls
 */
public record CaeResult(
    Status status,
    StopReason reason,
    String detail,
    Optional<CaeSnapshot> best,
    Optional<CaeSnapshot> last,
    List<RefinementEvent> history,
    CaeWork work) {
  /**
   * Copies the result and checks its complete snapshot relationship.
   *
   * @param status the status
   * @param reason the stopping reason
   * @param detail the explanation
   * @param best the optional retained approximation
   * @param last the optional final partition
   * @param history the committed events
   * @param work the operation counters
   * @throws IllegalArgumentException if snapshot presence, ordering, or the outcome is inconsistent
   */
  public CaeResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(best, "best");
    Objects.requireNonNull(last, "last");
    Objects.requireNonNull(work, "work");
    history = List.copyOf(history);
    if (status != reason.status()
        || best.isPresent() != last.isPresent()
        || (status == Status.CERTIFIED && best.isEmpty())) {
      throw new IllegalArgumentException("The stopping reason and complete snapshots must agree.");
    }
    if (last.isPresent()
        && (last.orElseThrow().refinement() != history.size()
            || best.orElseThrow().refinement() > last.orElseThrow().refinement()
            || best.orElseThrow().worstBound().compareTo(last.orElseThrow().worstBound()) > 0)) {
      throw new IllegalArgumentException(
          "The retained best snapshot must precede and dominate the last.");
    }
    if (last.isEmpty() && !history.isEmpty()) {
      throw new IllegalArgumentException("An uninitialized run cannot have committed refinements.");
    }
  }

  /** Solver outcome categories. */
  public enum Status {
    /** A complete snapshot meets the tolerance at every requested time. */
    CERTIFIED,
    /** A complete approximation is retained but a resource limit was reached. */
    BUDGET_EXHAUSTED,
    /** Cooperative cancellation interrupted the run. */
    CANCELLED,
    /** A verified numerical operation failed. */
    NUMERICAL_FAILURE
  }

  /** Specific stopping conditions, separate from the mathematical certificate. */
  public enum StopReason {
    /** The retained bound satisfies the requested tolerance. */
    TOLERANCE_REACHED(Status.CERTIFIED),
    /** The split budget is exhausted. */
    MAX_REFINEMENTS(Status.BUDGET_EXHAUSTED),
    /** Another split would exceed the active leaf budget. */
    MAX_CELLS(Status.BUDGET_EXHAUSTED),
    /** Every remaining leaf is at the depth limit. */
    MAX_DEPTH(Status.BUDGET_EXHAUSTED),
    /** The cancellation callback requested an interruption. */
    CANCEL_REQUESTED(Status.CANCELLED),
    /** Arithmetic could not provide the required enclosure. */
    ARITHMETIC_FAILURE(Status.NUMERICAL_FAILURE);

    private final Status status;

    StopReason(Status status) {
      this.status = status;
    }

    /**
     * Returns the outcome category associated with this condition.
     *
     * @return the status
     */
    public Status status() {
      return status;
    }
  }
}
