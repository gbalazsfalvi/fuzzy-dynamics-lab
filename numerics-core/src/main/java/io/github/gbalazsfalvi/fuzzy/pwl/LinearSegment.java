package io.github.gbalazsfalvi.fuzzy.pwl;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import java.util.Objects;
import java.util.Optional;

/**
 * A closed exact linear membership piece, with singleton spikes represented explicitly.
 *
 * @param domain the complete closed domain
 * @param leftGrade the left endpoint membership
 * @param rightGrade the right endpoint membership
 */
public record LinearSegment(RationalInterval domain, Rational leftGrade, Rational rightGrade) {
  /**
   * Validates grades; a singleton carries the maximum of its endpoint grades.
   *
   * @param domain the unit domain interval
   * @param leftGrade the left grade in [0,1]
   * @param rightGrade the right grade in [0,1]
   * @throws IllegalArgumentException if a grade is invalid
   */
  public LinearSegment {
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(leftGrade, "leftGrade");
    Objects.requireNonNull(rightGrade, "rightGrade");
    if (leftGrade.compareTo(Rational.ZERO) < 0
        || leftGrade.compareTo(Rational.ONE) > 0
        || rightGrade.compareTo(Rational.ZERO) < 0
        || rightGrade.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Linear membership grades must lie in [0,1].");
    }
    if (domain.lower().equals(domain.upper())) {
      leftGrade = leftGrade.max(rightGrade);
      rightGrade = leftGrade;
    }
  }

  /**
   * Evaluates this closed piece, assigning zero outside its domain.
   *
   * @param point the exact coordinate
   * @return the membership of this piece
   */
  public Rational at(Rational point) {
    if (!domain.contains(point)) {
      return Rational.ZERO;
    }
    if (domain.lower().equals(domain.upper())) {
      return leftGrade;
    }
    Rational fraction =
        point.subtract(domain.lower()).divide(domain.upper().subtract(domain.lower()));
    return leftGrade.add(rightGrade.subtract(leftGrade).multiply(fraction));
  }

  /**
   * Returns this piece's complete positive cut.
   *
   * @param alpha the membership level in (0,1]
   * @return its exact closed interval, or empty
   * @throws IllegalArgumentException if the level is outside (0,1]
   */
  public Optional<RationalInterval> cut(Rational alpha) {
    if (alpha.compareTo(Rational.ZERO) <= 0 || alpha.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Positive cuts require alpha in (0,1].");
    }
    boolean left = leftGrade.compareTo(alpha) >= 0;
    boolean right = rightGrade.compareTo(alpha) >= 0;
    if (left && right) {
      return Optional.of(domain);
    }
    if (!left && !right) {
      return Optional.empty();
    }
    Rational crossing =
        domain
            .lower()
            .add(
                domain
                    .upper()
                    .subtract(domain.lower())
                    .multiply(alpha.subtract(leftGrade).divide(rightGrade.subtract(leftGrade))));
    return Optional.of(
        left
            ? new RationalInterval(domain.lower(), crossing)
            : new RationalInterval(crossing, domain.upper()));
  }
}
