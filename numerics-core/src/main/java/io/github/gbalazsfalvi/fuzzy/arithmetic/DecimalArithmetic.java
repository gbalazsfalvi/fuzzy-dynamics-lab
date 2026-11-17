package io.github.gbalazsfalvi.fuzzy.arithmetic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Directed decimal interval arithmetic for rational, polynomial, and piecewise linear models.
 *
 * <p>FLOOR means toward negative infinity, not toward zero. Every endpoint operation explicitly
 * selects its rounding direction. Transcendental functions are deliberately outside this contract.
 *
 * @param precision the number of significant decimal digits
 */
public record DecimalArithmetic(int precision) {
  /**
   * Validates the precision.
   *
   * @param precision the positive number of significant digits
   * @throws IllegalArgumentException if the precision is not positive
   */
  public DecimalArithmetic {
    if (precision < 1) {
      throw new IllegalArgumentException("Decimal precision must be positive.");
    }
  }

  /**
   * Returns the rounding context for lower bounds.
   *
   * @return the downward context
   */
  public MathContext downward() {
    return new MathContext(precision, RoundingMode.FLOOR);
  }

  /**
   * Returns the rounding context for upper bounds.
   *
   * @return the upward context
   */
  public MathContext upward() {
    return new MathContext(precision, RoundingMode.CEILING);
  }

  /**
   * Encloses an exact fraction.
   *
   * @param value the fraction
   * @return a directed enclosure
   */
  public DecimalInterval enclose(Rational value) {
    return new DecimalInterval(value.toDecimal(downward()), value.toDecimal(upward()));
  }

  /**
   * Encloses an interval sum.
   *
   * @param left the first interval
   * @param right the second interval
   * @return the sum enclosure
   */
  public DecimalInterval add(DecimalInterval left, DecimalInterval right) {
    return new DecimalInterval(
        left.lower().add(right.lower(), downward()), left.upper().add(right.upper(), upward()));
  }

  /**
   * Encloses an interval difference.
   *
   * @param left the minuend
   * @param right the subtrahend
   * @return the difference enclosure
   */
  public DecimalInterval subtract(DecimalInterval left, DecimalInterval right) {
    return new DecimalInterval(
        left.lower().subtract(right.upper(), downward()),
        left.upper().subtract(right.lower(), upward()));
  }

  /**
   * Encloses all products, including operands with mixed signs.
   *
   * @param left the first interval
   * @param right the second interval
   * @return the product enclosure
   */
  public DecimalInterval multiply(DecimalInterval left, DecimalInterval right) {
    BigDecimal lo = null;
    BigDecimal hi = null;
    for (BigDecimal a : endpoints(left)) {
      for (BigDecimal b : endpoints(right)) {
        BigDecimal candidateLo = a.multiply(b, downward());
        BigDecimal candidateHi = a.multiply(b, upward());
        lo = lo == null ? candidateLo : lo.min(candidateLo);
        hi = hi == null ? candidateHi : hi.max(candidateHi);
      }
    }
    return new DecimalInterval(lo, hi);
  }

  /**
   * Encloses quotients when the divisor excludes zero.
   *
   * @param dividend the numerator interval
   * @param divisor the denominator interval
   * @return the quotient enclosure
   * @throws ArithmeticException if the divisor contains zero
   */
  public DecimalInterval divide(DecimalInterval dividend, DecimalInterval divisor) {
    if (divisor.contains(BigDecimal.ZERO)) {
      throw new ArithmeticException("Interval division requires a divisor excluding zero.");
    }
    BigDecimal lo = null;
    BigDecimal hi = null;
    for (BigDecimal a : endpoints(dividend)) {
      for (BigDecimal b : endpoints(divisor)) {
        BigDecimal candidateLo = a.divide(b, downward());
        BigDecimal candidateHi = a.divide(b, upward());
        lo = lo == null ? candidateLo : lo.min(candidateLo);
        hi = hi == null ? candidateHi : hi.max(candidateHi);
      }
    }
    return new DecimalInterval(lo, hi);
  }

  /**
   * Encloses the square without the dependency error of general interval multiplication.
   *
   * @param value the interval
   * @return the square range enclosure
   */
  public DecimalInterval square(DecimalInterval value) {
    BigDecimal a = value.lower();
    BigDecimal b = value.upper();
    BigDecimal lo =
        value.contains(BigDecimal.ZERO)
            ? BigDecimal.ZERO
            : a.multiply(a, downward()).min(b.multiply(b, downward()));
    BigDecimal hi = a.multiply(a, upward()).max(b.multiply(b, upward()));
    return new DecimalInterval(lo, hi);
  }

  /**
   * Computes the exact absolute value range of decimal endpoints.
   *
   * @param value the interval
   * @return its absolute value interval
   */
  public DecimalInterval abs(DecimalInterval value) {
    BigDecimal lo =
        value.contains(BigDecimal.ZERO)
            ? BigDecimal.ZERO
            : value.lower().abs().min(value.upper().abs());
    return new DecimalInterval(lo, value.lower().abs().max(value.upper().abs()));
  }

  /**
   * Encloses a pointwise maximum of two independently bounded quantities.
   *
   * @param left the first range
   * @param right the second range
   * @return the maximum range enclosure
   */
  public DecimalInterval maximum(DecimalInterval left, DecimalInterval right) {
    return new DecimalInterval(left.lower().max(right.lower()), left.upper().max(right.upper()));
  }

  /**
   * Rounds an exact nonnegative width upward.
   *
   * @param value the interval
   * @return an upper bound on its width
   */
  public BigDecimal width(DecimalInterval value) {
    return value.upper().subtract(value.lower(), upward());
  }

  private static BigDecimal[] endpoints(DecimalInterval interval) {
    return new BigDecimal[] {interval.lower(), interval.upper()};
  }
}
