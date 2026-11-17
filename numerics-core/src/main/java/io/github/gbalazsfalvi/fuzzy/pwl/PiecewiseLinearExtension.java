package io.github.gbalazsfalvi.fuzzy.pwl;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Exact specialization of the linear transport kernel in Kupka and Skorupova (2021), Section 4.1.
 *
 * <p>Source pieces are split at map knots, their endpoints are transported, and overlapping outputs
 * are interpreted by their pointwise maximum. This implementation retains implicit overlapping
 * pieces and exact fractions; it does not reproduce the paper's PSO preprocessing or reported
 * times.
 */
public final class PiecewiseLinearExtension {
  private PiecewiseLinearExtension() {}

  /**
   * Transports the complete state through an exact continuous piecewise linear map.
   *
   * @param state the complete source membership representation
   * @param map the full unit self map
   * @param maxPieces the positive maximum output piece count
   * @return the exact output and actual map evaluation count
   * @throws IllegalArgumentException if the piece budget is invalid
   * @throws PieceLimitException if the complete output would exceed the declared budget
   */
  public static Transport extend(PiecewiseLinearState state, ExactLinearMap map, int maxPieces) {
    if (maxPieces < 1) {
      throw new IllegalArgumentException("The PWL piece budget must be positive.");
    }
    List<LinearSegment> output = new ArrayList<>();
    long evaluations = 0;
    for (LinearSegment piece : state.pieces()) {
      TreeSet<Rational> coordinates =
          new TreeSet<>(List.of(piece.domain().lower(), piece.domain().upper()));
      for (LinearKnot knot : map.knots()) {
        if (piece.domain().contains(knot.coordinate())) {
          coordinates.add(knot.coordinate());
        }
      }
      List<Rational> ordered = new ArrayList<>(coordinates);
      // A spike has no adjacent coordinate pair; explicitly keep its transported maximum grade.
      int count = Math.max(1, ordered.size() - 1);
      for (int index = 0; index < count; index++) {
        Rational left = ordered.get(index);
        Rational right = ordered.get(Math.min(index + 1, ordered.size() - 1));
        Rational imageLeft = map.at(left);
        Rational imageRight = map.at(right);
        evaluations = Math.addExact(evaluations, 2);
        if (output.size() >= maxPieces) {
          throw new PieceLimitException(evaluations, output.size());
        }
        Rational gradeLeft = piece.at(left);
        Rational gradeRight = piece.at(right);
        output.add(
            imageLeft.compareTo(imageRight) <= 0
                ? new LinearSegment(
                    new RationalInterval(imageLeft, imageRight), gradeLeft, gradeRight)
                : new LinearSegment(
                    new RationalInterval(imageRight, imageLeft), gradeRight, gradeLeft));
      }
    }
    return new Transport(new PiecewiseLinearState(output), evaluations);
  }

  /**
   * A complete exact extension and its numerical work.
   *
   * @param state the complete output
   * @param exactPointEvaluations the actual exact map evaluations
   */
  public record Transport(PiecewiseLinearState state, long exactPointEvaluations) {
    /**
     * Validates complete output and count.
     *
     * @param state the complete state
     * @param exactPointEvaluations the nonnegative evaluation count
     * @throws IllegalArgumentException if the counter is negative
     */
    public Transport {
      Objects.requireNonNull(state, "state");
      if (exactPointEvaluations < 0) {
        throw new IllegalArgumentException("Exact map work must be nonnegative.");
      }
    }
  }

  /** An incomplete output is never exposed as a valid extension after a resource exit. */
  public static final class PieceLimitException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Actual exact map work before the resource exit. */
    private final long exactPointEvaluations;

    /** Fragments built before the incomplete output was discarded. */
    private final int completedPieces;

    PieceLimitException(long exactPointEvaluations, int completedPieces) {
      super("The complete PWL extension exceeds the declared piece budget.");
      this.exactPointEvaluations = exactPointEvaluations;
      this.completedPieces = completedPieces;
    }

    /**
     * Returns work spent, including the rejected fragment.
     *
     * @return the actual evaluation count
     */
    public long exactPointEvaluations() {
      return exactPointEvaluations;
    }

    /**
     * Returns how many pieces were built before abandoning the incomplete output.
     *
     * @return the constructed piece count
     */
    public int completedPieces() {
      return completedPieces;
    }
  }
}
