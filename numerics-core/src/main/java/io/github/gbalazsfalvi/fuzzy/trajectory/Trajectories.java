package io.github.gbalazsfalvi.fuzzy.trajectory;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Trajectory primitives for exact references and verified interval propagation. */
public final class Trajectories {
  private Trajectories() {}

  /**
   * Iterates small exact references, including the initial state.
   *
   * @param map the supported map
   * @param initial the exact initial point
   * @param horizon the nonnegative number of transitions
   * @return the immutable sequence at times zero through horizon
   * @throws IllegalArgumentException if the horizon or initial point is invalid
   */
  public static List<ExactPoint> exact(CertifiedMap map, ExactPoint initial, int horizon) {
    requireHorizon(horizon);
    UnitDomain.require(initial, map.dimension());
    List<ExactPoint> result = new ArrayList<>();
    ExactPoint state = initial;
    result.add(state);
    for (int time = 0; time < horizon; time++) {
      state = map.evaluateExact(state);
      result.add(state);
    }
    return List.copyOf(result);
  }

  /**
   * Evaluates the affine trajectory using its independent closed form.
   *
   * @param map the affine contraction
   * @param initial the exact initial coordinate in [0,1]
   * @param time the nonnegative requested time
   * @return factor^time * initial + (1 - factor^time)/2
   * @throws IllegalArgumentException if time or the initial coordinate is invalid
   */
  public static Rational affineClosedForm(AffineContraction map, Rational initial, int time) {
    requireHorizon(time);
    UnitDomain.require(new ExactPoint(List.of(initial)), 1);
    Rational factor = map.factor().pow(time);
    return factor.multiply(initial).add(Rational.ONE.subtract(factor).divide(Rational.TWO));
  }

  /**
   * Propagates a complete box while retaining representative evaluation residuals.
   *
   * <p>Interval dependency may make the boxes conservative. This routine is not the adaptive CAE
   * method and does not claim a fuzzy endograph error certificate.
   *
   * @param map the supported unit self map
   * @param initial the entire initial box
   * @param horizon the nonnegative number of transitions
   * @param arithmetic the directed rounding policy
   * @return immutable enclosures at times zero through horizon
   * @throws IllegalArgumentException if the horizon or input domain is invalid
   */
  public static List<TrajectoryStep> enclose(
      CertifiedMap map, Box initial, int horizon, DecimalArithmetic arithmetic) {
    requireHorizon(horizon);
    UnitDomain.require(initial, map.dimension());
    Box reachable = initial;
    DecimalPoint representative = initial.midpoint();
    BigDecimal residual = BigDecimal.ZERO;
    List<TrajectoryStep> result = new ArrayList<>();
    for (int time = 0; time <= horizon; time++) {
      result.add(
          new TrajectoryStep(
              time,
              reachable,
              representative,
              reachable.radiusAround(representative, arithmetic),
              residual));
      if (time < horizon) {
        Box pointImage = map.image(Box.point(representative), arithmetic);
        representative = pointImage.midpoint();
        residual = pointImage.radiusAround(representative, arithmetic);
        reachable = map.image(reachable, arithmetic);
      }
    }
    return List.copyOf(result);
  }

  private static void requireHorizon(int horizon) {
    if (horizon < 0) {
      throw new IllegalArgumentException("A trajectory horizon must be nonnegative.");
    }
  }
}
