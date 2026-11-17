package io.github.gbalazsfalvi.fuzzy.membership;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MembershipRangesTest {
  @Test
  void validatesBothRangeDomainsAndTheirRequiredOverlap() {
    DecimalArithmetic arithmetic = new DecimalArithmetic(8);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MembershipSample.fromRanges(
                DecimalInterval.of("-0.1", "0.5"), DecimalInterval.of("0.1", "0.2"), arithmetic));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MembershipSample.fromRanges(
                DecimalInterval.of("0", "1"), DecimalInterval.of("0.5", "1.1"), arithmetic));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MembershipSample.fromRanges(
                DecimalInterval.of("0", "0.1"), DecimalInterval.of("0.2", "0.3"), arithmetic));
    MembershipSample sample =
        MembershipSample.fromRanges(
            DecimalInterval.of("0", "1"), DecimalInterval.of("0.4", "0.5"), arithmetic);
    assertEquals(new BigDecimal("0.4"), sample.grade());
    assertEquals(new BigDecimal("0.1"), sample.gradeError());
    assertEquals(new BigDecimal("0.6"), sample.variation());
    assertEquals(BigDecimal.ONE, sample.upperMembership());
  }
}
