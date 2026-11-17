package io.github.gbalazsfalvi.fuzzy.membership;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import java.math.BigDecimal;

/** A continuous membership profile with whole box range and variation information. */
public sealed interface CertifiedMembership permits TentPeak, MultiPeakMembership {
  /**
   * Returns the state dimension.
   *
   * @return the positive dimension
   */
  int dimension();

  /**
   * Encloses membership values over an entire box.
   *
   * @param input the box inside the unit domain
   * @param arithmetic the directed rounding policy
   * @return a range contained in [0,1]
   */
  DecimalInterval range(Box input, DecimalArithmetic arithmetic);

  /**
   * Evaluates an exact reference value.
   *
   * @param input the exact point in the unit domain
   * @return its membership in [0,1]
   */
  Rational evaluateExact(ExactPoint input);

  /**
   * Bounds variation in the spatial infinity norm over the unit domain.
   *
   * @param arithmetic the upward rounding policy
   * @return a nonnegative global Lipschitz bound
   */
  BigDecimal lipschitzBound(DecimalArithmetic arithmetic);
}
