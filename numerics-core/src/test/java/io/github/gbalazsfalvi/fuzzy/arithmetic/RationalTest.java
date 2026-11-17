package io.github.gbalazsfalvi.fuzzy.arithmetic;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class RationalTest {
  @Test
  void normalizesSignsZerosAndEquivalentFractions() {
    assertEquals(Rational.of(1, 2), Rational.of(-2, -4));
    assertEquals(Rational.of(-1, 2), Rational.of(2, -4));
    assertEquals(BigInteger.ONE, Rational.of(0, -500).denominator());
    assertEquals(Rational.ONE.hashCode(), Rational.of(23, 23).hashCode());
    assertEquals("1/2", Rational.of(2, 4).toString());
    assertEquals("0", Rational.ZERO.toString());
  }

  @Test
  void parsesDecimalsAndScientificNotationWithoutBinaryRounding() {
    assertEquals(Rational.of(1, 10), Rational.parse("0.1"));
    assertEquals(Rational.of(-1, 8), Rational.parse("-0.125"));
    assertEquals(Rational.of(1200), Rational.parse("1.2e3"));
    assertEquals(Rational.of(1, 800), Rational.parse("1.25E-3"));
    assertEquals(Rational.of(2, 3), Rational.parse(" 4 / 6 "));
    assertEquals(Rational.of(1, 10), Rational.fromDecimal(new BigDecimal("0.1000")));
    assertThrows(NumberFormatException.class, () -> Rational.parse("1/2/3"));
  }

  @Test
  void computesExactArithmeticAndOrder() {
    Rational third = Rational.of(1, 3);
    Rational fifth = Rational.of(1, 5);
    assertEquals(Rational.of(8, 15), third.add(fifth));
    assertEquals(Rational.of(2, 15), third.subtract(fifth));
    assertEquals(Rational.of(1, 15), third.multiply(fifth));
    assertEquals(Rational.of(5, 3), third.divide(fifth));
    assertEquals(Rational.of(1, 27), third.pow(3));
    assertEquals(Rational.ONE, Rational.ZERO.pow(0));
    assertEquals(third, third.negate().abs());
    assertEquals(fifth, third.min(fifth));
    assertEquals(third, third.max(fifth));
    assertEquals(third, third.min(third));
    assertEquals(fifth, fifth.max(fifth));
    assertEquals(new BigDecimal("0.3333"), third.toDecimal(new MathContext(4)));
  }

  @Test
  void rejectsUndefinedReferenceOperations() {
    assertThrows(ArithmeticException.class, () -> Rational.of(1, 0));
    assertThrows(ArithmeticException.class, () -> Rational.ONE.divide(Rational.ZERO));
    assertThrows(IllegalArgumentException.class, () -> Rational.TWO.pow(-1));
    assertThrows(NullPointerException.class, () -> new Rational(null, BigInteger.ONE));
  }
}
