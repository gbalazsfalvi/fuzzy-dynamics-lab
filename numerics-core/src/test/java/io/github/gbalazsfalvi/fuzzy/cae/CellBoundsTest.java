package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CellBoundsTest {
  @Test
  void roundsTheVerticalErrorUpwardAndAppliesTheIndependentBaselineCap() {
    DecimalArithmetic arithmetic = new DecimalArithmetic(2);
    MembershipSample sample =
        new MembershipSample(
            new BigDecimal("0.3"),
            new BigDecimal("0.017"),
            new BigDecimal("0.0241"),
            new BigDecimal("0.9"));
    assertEquals(
        new BigDecimal("0.042"), CellBounds.endograph(sample, new BigDecimal("0.01"), arithmetic));
    assertEquals(
        new BigDecimal("0.5"), CellBounds.endograph(sample, new BigDecimal("0.5"), arithmetic));
    MembershipSample weak =
        new MembershipSample(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("0.01"));
    assertEquals(
        new BigDecimal("0.01"), CellBounds.endograph(weak, new BigDecimal("100"), arithmetic));
    assertThrows(
        IllegalArgumentException.class,
        () -> CellBounds.endograph(sample, new BigDecimal("-0.01"), arithmetic));
  }
}
