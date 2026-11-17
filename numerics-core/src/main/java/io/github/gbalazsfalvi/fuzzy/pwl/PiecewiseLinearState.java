package io.github.gbalazsfalvi.fuzzy.pwl;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cuts.IntervalUnion;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import io.github.gbalazsfalvi.fuzzy.membership.CertifiedMembership;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.util.ArrayList;
import java.util.List;

/**
 * A finite pointwise maximum of closed linear pieces over the full common zero baseline.
 *
 * <p>Overlaps, jumps at support boundaries, and singleton spikes are retained exactly. No sampling,
 * normalization, or convex hull replaces this representation.
 *
 * @param pieces the immutable membership pieces, possibly empty for the zero state
 */
public record PiecewiseLinearState(List<LinearSegment> pieces) {
  /**
   * Copies the exact pieces without discarding any positive contribution.
   *
   * @param pieces the possibly empty linear piece list
   */
  public PiecewiseLinearState {
    pieces = List.copyOf(pieces);
  }

  /**
   * Constructs the exact maximum of supported scalar peak profiles.
   *
   * @param membership the one dimensional supported profile
   * @return its complete piecewise linear representation
   * @throws IllegalArgumentException if the profile is not scalar
   */
  public static PiecewiseLinearState from(CertifiedMembership membership) {
    if (membership.dimension() != 1) {
      throw new IllegalArgumentException("The PWL membership representation is scalar.");
    }
    List<TentPeak> peaks =
        switch (membership) {
          case TentPeak peak -> List.of(peak);
          case MultiPeakMembership profile -> profile.peaks();
        };
    List<LinearSegment> pieces = new ArrayList<>();
    for (TentPeak peak : peaks) {
      Rational center = peak.center().coordinate(0);
      Rational left = center.subtract(peak.width()).max(Rational.ZERO);
      Rational right = center.add(peak.width()).min(Rational.ONE);
      Rational leftGrade =
          peak.height()
              .multiply(
                  Rational.ONE
                      .subtract(center.subtract(left).divide(peak.width()))
                      .max(Rational.ZERO));
      Rational rightGrade =
          peak.height()
              .multiply(
                  Rational.ONE
                      .subtract(right.subtract(center).divide(peak.width()))
                      .max(Rational.ZERO));
      pieces.add(new LinearSegment(new RationalInterval(left, center), leftGrade, peak.height()));
      pieces.add(new LinearSegment(new RationalInterval(center, right), peak.height(), rightGrade));
    }
    return new PiecewiseLinearState(pieces);
  }

  /**
   * Evaluates the maximum of all exact contributions.
   *
   * @param coordinate the coordinate
   * @return zero outside every positive piece, otherwise their maximum
   */
  public Rational at(Rational coordinate) {
    return pieces.stream()
        .map(piece -> piece.at(coordinate))
        .max(Rational::compareTo)
        .orElse(Rational.ZERO);
  }

  /**
   * Returns a complete positive cut by unioning every contributing segment cut.
   *
   * @param alpha the level in (0,1]
   * @return the full exact interval union, including empty cuts
   * @throws IllegalArgumentException if the level is outside (0,1]
   */
  public IntervalUnion cut(Rational alpha) {
    if (alpha.compareTo(Rational.ZERO) <= 0 || alpha.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Positive cuts require alpha in (0,1].");
    }
    return new IntervalUnion(pieces.stream().flatMap(piece -> piece.cut(alpha).stream()).toList());
  }
}
