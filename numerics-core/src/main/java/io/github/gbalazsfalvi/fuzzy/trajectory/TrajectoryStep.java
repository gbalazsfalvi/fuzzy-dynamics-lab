package io.github.gbalazsfalvi.fuzzy.trajectory;

import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One step produced by the verified trajectory primitive.
 *
 * <p>The mathematical meaning of reachable and residual follows from the originating map and
 * arithmetic. Constructing a record directly is not an independent certificate validation.
 *
 * @param time the nonnegative discrete time
 * @param reachable the interval enclosure of the initial box image
 * @param representative the chosen finite decimal representative
 * @param radius an upper bound on distance from that representative to the whole enclosure
 * @param evaluationResidual the last representative evaluation error, or zero at time zero
 */
public record TrajectoryStep(
    int time,
    Box reachable,
    DecimalPoint representative,
    BigDecimal radius,
    BigDecimal evaluationResidual) {
  /**
   * Checks dimensions and nonnegative quantities.
   *
   * @param time the nonnegative time
   * @param reachable the reachable enclosure
   * @param representative the decimal representative
   * @param radius the nonnegative spatial radius
   * @param evaluationResidual the nonnegative evaluation residual
   * @throws IllegalArgumentException if dimensions disagree or a quantity is negative
   */
  public TrajectoryStep {
    Objects.requireNonNull(reachable, "reachable");
    Objects.requireNonNull(representative, "representative");
    Objects.requireNonNull(radius, "radius");
    Objects.requireNonNull(evaluationResidual, "evaluationResidual");
    if (time < 0
        || reachable.dimension() != representative.dimension()
        || radius.signum() < 0
        || evaluationResidual.signum() < 0) {
      throw new IllegalArgumentException(
          "Trajectory step dimensions and bounds must be consistent.");
    }
  }
}
