package io.github.gbalazsfalvi.fuzzy.arithmetic;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecimalArithmeticTest {
  @ParameterizedTest
  @ValueSource(ints = {1, 3, 12, 34})
  void enclosesIndependentExactRationalCalculations(int precision) {
    DecimalArithmetic arithmetic = new DecimalArithmetic(precision);
    Random random = new Random(20261117);
    for (int trial = 0; trial < 200; trial++) {
      DecimalInterval left = interval(random);
      DecimalInterval right = interval(random);
      Rational x = interior(left, trial % 8);
      Rational y = interior(right, (trial * 3) % 8);
      assertTrue(arithmetic.add(left, right).contains(x.add(y)), "sum " + trial);
      assertTrue(arithmetic.subtract(left, right).contains(x.subtract(y)), "difference " + trial);
      assertTrue(arithmetic.multiply(left, right).contains(x.multiply(y)), "product " + trial);
      assertTrue(arithmetic.square(left).contains(x.multiply(x)), "square " + trial);
      assertTrue(arithmetic.abs(left).contains(x.abs()), "absolute value " + trial);
      assertTrue(arithmetic.maximum(left, right).contains(x.max(y)), "maximum " + trial);
      if (!right.contains(BigDecimal.ZERO)) {
        assertTrue(arithmetic.divide(left, right).contains(x.divide(y)), "quotient " + trial);
      }
    }
  }

  @Test
  void roundsNegativeBoundsOutwardAndPreservesInteriorExtrema() {
    DecimalArithmetic arithmetic = new DecimalArithmetic(3);
    assertEquals(DecimalInterval.of("-0.334", "-0.333"), arithmetic.enclose(Rational.of(-1, 3)));
    assertEquals(DecimalInterval.of("0.333", "0.334"), arithmetic.enclose(Rational.of(1, 3)));
    assertEquals(DecimalInterval.of("0", "9"), arithmetic.square(DecimalInterval.of("-3", "2")));
    assertEquals(DecimalInterval.of("4", "9"), arithmetic.square(DecimalInterval.of("-3", "-2")));
    assertEquals(DecimalInterval.of("2", "3"), arithmetic.abs(DecimalInterval.of("2", "3")));
    assertEquals(DecimalInterval.of("0", "3"), arithmetic.abs(DecimalInterval.of("-3", "2")));
    assertEquals(
        DecimalInterval.of("1", "1"),
        arithmetic.multiply(DecimalInterval.of("-1", "-1"), DecimalInterval.of("-1", "-1")));
    assertEquals(
        DecimalInterval.of("0.000000001", "0.000000001"),
        arithmetic.enclose(Rational.parse("1e-9")));
  }

  @Test
  void handlesClosedIntersectionScaleAndExactMidpoint() {
    DecimalInterval interval = DecimalInterval.of("0.10", "0.30");
    assertEquals(DecimalInterval.of("0.1", "0.3"), interval);
    assertEquals(new BigDecimal("0.2"), interval.midpoint());
    assertEquals(
        DecimalInterval.of("0.2", "0.3"),
        interval.intersection(DecimalInterval.of("0.2", "1")).orElseThrow());
    assertTrue(interval.intersection(DecimalInterval.of("0.4", "1")).isEmpty());
    assertEquals(DecimalInterval.of("0.1", "1"), interval.hull(DecimalInterval.of("0.2", "1")));
    assertTrue(interval.contains(DecimalInterval.of("0.2", "0.25")));
    assertFalse(interval.contains(DecimalInterval.of("0.2", "0.4")));
    assertFalse(interval.contains(new BigDecimal("0.09")));
    assertFalse(interval.contains(Rational.ONE));
    assertEquals(new BigDecimal("0.2"), new DecimalArithmetic(3).width(interval));
  }

  @Test
  void rejectsInvalidPrecisionEndpointsAndZeroContainingDivisors() {
    assertThrows(IllegalArgumentException.class, () -> new DecimalArithmetic(0));
    assertThrows(IllegalArgumentException.class, () -> DecimalInterval.of("1", "0"));
    DecimalArithmetic arithmetic = new DecimalArithmetic(4);
    for (DecimalInterval divisor :
        new DecimalInterval[] {
          DecimalInterval.of("-1", "1"), DecimalInterval.of("0", "1"),
          DecimalInterval.of("-1", "0"), DecimalInterval.of("0", "0")
        }) {
      assertThrows(
          ArithmeticException.class,
          () -> arithmetic.divide(DecimalInterval.of("1", "2"), divisor));
    }
  }

  private static DecimalInterval interval(Random random) {
    BigDecimal first = BigDecimal.valueOf(random.nextInt(2001) - 1000, 2);
    BigDecimal second = BigDecimal.valueOf(random.nextInt(2001) - 1000, 2);
    return new DecimalInterval(first.min(second), first.max(second));
  }

  private static Rational interior(DecimalInterval interval, int part) {
    Rational lower = Rational.fromDecimal(interval.lower());
    Rational width = Rational.fromDecimal(interval.upper()).subtract(lower);
    return lower.add(width.multiply(Rational.of(part, 7)));
  }
}
