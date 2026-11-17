package io.github.gbalazsfalvi.fuzzy.fuzzy;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.util.Objects;

/**
 * One exact location and membership grade in a finite reference state.
 *
 * @param location the exact location in its unit domain
 * @param grade the exact grade in [0,1]
 */
public record FuzzyAtom(ExactPoint location, Rational grade) {
  /**
   * Validates the location and membership.
   *
   * @param location the location inside the unit domain
   * @param grade the value in [0,1]
   * @throws IllegalArgumentException if the location or grade is invalid
   */
  public FuzzyAtom {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(grade, "grade");
    UnitDomain.require(location, location.dimension());
    if (grade.compareTo(Rational.ZERO) < 0 || grade.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("Membership grades must lie in [0,1].");
    }
  }
}
