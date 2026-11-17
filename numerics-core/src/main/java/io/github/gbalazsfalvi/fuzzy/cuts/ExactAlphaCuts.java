package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.membership.CertifiedMembership;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Independent exact one dimensional cut propagation for the built in maps and peak profiles.
 *
 * <p>Endpoint values and the known turning point determine each complete continuous interval image.
 * No CAE cell recipe, directed decimal enclosure, or sampled extrema are used here.
 */
public final class ExactAlphaCuts {
  private ExactAlphaCuts() {}

  /**
   * Constructs a complete positive alpha cut, including separated peaks and singleton cores.
   *
   * @param membership the supported scalar initial profile
   * @param alpha the level in (0,1]
   * @return the exact interval union, possibly empty
   * @throws IllegalArgumentException if the profile is not scalar or the level is invalid
   */
  public static IntervalUnion initial(CertifiedMembership membership, Rational alpha) {
    if (membership.dimension() != 1
        || alpha.compareTo(Rational.ZERO) <= 0
        || alpha.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Exact scalar cuts require a level in (0,1].");
    }
    List<TentPeak> peaks =
        switch (membership) {
          case TentPeak peak -> List.of(peak);
          case MultiPeakMembership profile -> profile.peaks();
        };
    List<RationalInterval> intervals = new ArrayList<>();
    for (TentPeak peak : peaks) {
      if (peak.height().compareTo(alpha) >= 0) {
        Rational radius = peak.width().multiply(Rational.ONE.subtract(alpha.divide(peak.height())));
        Rational center = peak.center().coordinate(0);
        intervals.add(
            new RationalInterval(
                center.subtract(radius).max(Rational.ZERO), center.add(radius).min(Rational.ONE)));
      }
    }
    return new IntervalUnion(intervals);
  }

  /**
   * Transports every interval and records actual exact map evaluation attempts.
   *
   * @param map the supported scalar unit self map
   * @param input the complete input union
   * @return the exact image and operation counts
   * @throws IllegalArgumentException if the map is not one dimensional
   */
  public static Image image(CertifiedMap map, IntervalUnion input) {
    if (map.dimension() != 1) {
      throw new IllegalArgumentException("Exact interval union propagation is one dimensional.");
    }
    List<RationalInterval> images = new ArrayList<>();
    long evaluations = 0;
    for (RationalInterval interval : input.intervals()) {
      Rational first = evaluate(map, interval.lower());
      Rational second = evaluate(map, interval.upper());
      evaluations = Math.addExact(evaluations, 2);
      Rational lower = first.min(second);
      Rational upper = first.max(second);
      Rational critical = Rational.of(1, 2);
      // The affine map has no interior extremum. Tent and logistic maps turn at one half.
      if (!(map instanceof io.github.gbalazsfalvi.fuzzy.maps.AffineContraction)
          && interval.contains(critical)) {
        Rational turningValue = evaluate(map, critical);
        evaluations = Math.incrementExact(evaluations);
        lower = lower.min(turningValue);
        upper = upper.max(turningValue);
      }
      images.add(new RationalInterval(lower, upper));
    }
    return new Image(new IntervalUnion(images), evaluations, input.intervals().size());
  }

  private static Rational evaluate(CertifiedMap map, Rational coordinate) {
    return map.evaluateExact(new ExactPoint(List.of(coordinate))).coordinate(0);
  }

  /**
   * Exact cut image with numerical work that is distinct from decimal interval evaluations.
   *
   * @param cuts the full output union
   * @param exactPointEvaluations the actual endpoint and turning point evaluations
   * @param intervalImages the input components transported before merging
   */
  public record Image(IntervalUnion cuts, long exactPointEvaluations, long intervalImages) {
    /**
     * Validates immutable output and nonnegative counters.
     *
     * @param cuts the complete union
     * @param exactPointEvaluations the exact evaluation count
     * @param intervalImages the component image count
     * @throws IllegalArgumentException if a count is negative
     */
    public Image {
      Objects.requireNonNull(cuts, "cuts");
      if (exactPointEvaluations < 0 || intervalImages < 0) {
        throw new IllegalArgumentException("Cut image counters must be nonnegative.");
      }
    }
  }
}
