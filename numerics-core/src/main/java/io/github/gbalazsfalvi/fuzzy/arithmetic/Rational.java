package io.github.gbalazsfalvi.fuzzy.arithmetic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * An exact reduced fraction used for model parameters and small reference calculations.
 *
 * <p>No operation converts through binary floating point. Numerators can grow rapidly when
 * nonlinear maps are iterated; this type is a reference tool, not a claim of efficient arbitrary
 * precision.
 *
 * @param numerator the signed numerator
 * @param denominator the strictly positive denominator after normalization
 */
public record Rational(BigInteger numerator, BigInteger denominator)
    implements Comparable<Rational> {
  /** The additive identity. */
  public static final Rational ZERO = of(0);

  /** The multiplicative identity. */
  public static final Rational ONE = of(1);

  /** The integer two. */
  public static final Rational TWO = of(2);

  /**
   * Reduces a fraction and places its sign in the numerator.
   *
   * @param numerator the numerator
   * @param denominator the nonzero denominator
   * @throws ArithmeticException if the denominator is zero
   */
  public Rational {
    Objects.requireNonNull(numerator, "numerator");
    Objects.requireNonNull(denominator, "denominator");
    if (denominator.signum() == 0) {
      throw new ArithmeticException("A rational denominator must be nonzero.");
    }
    if (denominator.signum() < 0) {
      numerator = numerator.negate();
      denominator = denominator.negate();
    }
    BigInteger divisor = numerator.gcd(denominator);
    numerator = numerator.divide(divisor);
    denominator = denominator.divide(divisor);
  }

  /**
   * Creates an integer fraction.
   *
   * @param value the exact integer
   * @return the fraction
   */
  public static Rational of(long value) {
    return new Rational(BigInteger.valueOf(value), BigInteger.ONE);
  }

  /**
   * Creates a fraction from integer components.
   *
   * @param numerator the numerator
   * @param denominator the nonzero denominator
   * @return the reduced fraction
   */
  public static Rational of(long numerator, long denominator) {
    return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
  }

  /**
   * Parses an integer, decimal, scientific decimal, or slash separated fraction exactly.
   *
   * @param text the exact textual value
   * @return the fraction
   * @throws NumberFormatException if the text is not a supported number
   * @throws ArithmeticException if a denominator is zero
   */
  public static Rational parse(String text) {
    Objects.requireNonNull(text, "text");
    String value = text.trim();
    int slash = value.indexOf('/');
    if (slash >= 0) {
      return new Rational(
          new BigInteger(value.substring(0, slash).trim()),
          new BigInteger(value.substring(slash + 1).trim()));
    }
    return fromDecimal(new BigDecimal(value));
  }

  /**
   * Interprets an existing decimal as an exact value.
   *
   * @param value the finite decimal
   * @return its exact fraction
   */
  public static Rational fromDecimal(BigDecimal value) {
    Objects.requireNonNull(value, "value");
    if (value.scale() < 0) {
      return new Rational(
          value.unscaledValue().multiply(BigInteger.TEN.pow(Math.negateExact(value.scale()))),
          BigInteger.ONE);
    }
    return new Rational(value.unscaledValue(), BigInteger.TEN.pow(value.scale()));
  }

  /**
   * Adds an exact fraction.
   *
   * @param other the addend
   * @return the exact sum
   */
  public Rational add(Rational other) {
    return new Rational(
        numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
        denominator.multiply(other.denominator));
  }

  /**
   * Subtracts an exact fraction.
   *
   * @param other the subtrahend
   * @return the exact difference
   */
  public Rational subtract(Rational other) {
    return add(other.negate());
  }

  /**
   * Multiplies exact fractions.
   *
   * @param other the multiplier
   * @return the exact product
   */
  public Rational multiply(Rational other) {
    return new Rational(
        numerator.multiply(other.numerator), denominator.multiply(other.denominator));
  }

  /**
   * Divides exact fractions.
   *
   * @param other the nonzero divisor
   * @return the exact quotient
   * @throws ArithmeticException if the divisor is zero
   */
  public Rational divide(Rational other) {
    return new Rational(
        numerator.multiply(other.denominator), denominator.multiply(other.numerator));
  }

  /**
   * Changes the sign.
   *
   * @return the exact opposite
   */
  public Rational negate() {
    return new Rational(numerator.negate(), denominator);
  }

  /**
   * Takes the absolute value.
   *
   * @return the nonnegative fraction
   */
  public Rational abs() {
    return new Rational(numerator.abs(), denominator);
  }

  /**
   * Raises the value to a nonnegative integer power.
   *
   * @param exponent the nonnegative exponent
   * @return the exact power, including one for exponent zero
   * @throws IllegalArgumentException if the exponent is negative
   */
  public Rational pow(int exponent) {
    if (exponent < 0) {
      throw new IllegalArgumentException("The reference exponent must be nonnegative.");
    }
    return new Rational(numerator.pow(exponent), denominator.pow(exponent));
  }

  /**
   * Chooses the smaller value.
   *
   * @param other the comparison value
   * @return the minimum
   */
  public Rational min(Rational other) {
    return compareTo(other) <= 0 ? this : other;
  }

  /**
   * Chooses the larger value.
   *
   * @param other the comparison value
   * @return the maximum
   */
  public Rational max(Rational other) {
    return compareTo(other) >= 0 ? this : other;
  }

  /**
   * Converts using an explicit decimal rounding policy.
   *
   * @param context the precision and rounding mode
   * @return the rounded decimal
   */
  public BigDecimal toDecimal(MathContext context) {
    return new BigDecimal(numerator).divide(new BigDecimal(denominator), context);
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(Rational other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return denominator.equals(BigInteger.ONE)
        ? numerator.toString()
        : numerator + "/" + denominator;
  }
}
