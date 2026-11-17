package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.Objects;

/**
 * An exact cut diagnostic that keeps empty cut mismatches separate from Hausdorff distances.
 *
 * @param status the emptiness relationship
 * @param distance the exact distance only when both cuts are nonempty; otherwise null
 */
public record CutComparison(Status status, Rational distance) {
  /**
   * Validates distance presence and sign.
   *
   * @param status the emptiness classification
   * @param distance the nonnegative distance for two nonempty cuts, otherwise null
   * @throws IllegalArgumentException if presence or sign disagrees with the status
   */
  public CutComparison {
    Objects.requireNonNull(status, "status");
    if ((status == Status.NONEMPTY) != (distance != null)
        || (distance != null && distance.compareTo(Rational.ZERO) < 0)) {
      throw new IllegalArgumentException("Empty cuts do not have an ordinary Hausdorff distance.");
    }
  }

  /**
   * Compares complete interval unions, preserving an explicit empty status.
   *
   * @param reference the exact reference cut
   * @param approximation the approximation cut
   * @return an exact diagnostic
   */
  public static CutComparison compare(IntervalUnion reference, IntervalUnion approximation) {
    boolean leftEmpty = reference.intervals().isEmpty();
    boolean rightEmpty = approximation.intervals().isEmpty();
    if (leftEmpty || rightEmpty) {
      Status status =
          leftEmpty
              ? (rightEmpty ? Status.BOTH_EMPTY : Status.REFERENCE_EMPTY)
              : Status.APPROXIMATION_EMPTY;
      return new CutComparison(status, null);
    }
    return new CutComparison(Status.NONEMPTY, reference.hausdorff(approximation));
  }

  /** Ordinary compact set Hausdorff distance is recorded only for NONEMPTY. */
  public enum Status {
    /** Both cuts are nonempty. */
    NONEMPTY,
    /** Both cuts are empty; no nonempty set distance is asserted. */
    BOTH_EMPTY,
    /** Only the reference cut is empty. */
    REFERENCE_EMPTY,
    /** Only the approximation cut is empty. */
    APPROXIMATION_EMPTY
  }
}
