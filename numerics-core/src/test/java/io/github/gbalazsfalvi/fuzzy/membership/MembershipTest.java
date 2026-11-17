package io.github.gbalazsfalvi.fuzzy.membership;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MembershipTest {
  private final MultiPeakMembership separated =
      new MultiPeakMembership(
          List.of(
              new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.ONE),
              new TentPeak(ExactPoint.of("3/4"), Rational.of(1, 8), Rational.of(1, 100))));

  @Test
  void preservesSeparatePeaksAndTheirDifferentHeights() {
    assertEquals(Rational.ONE, separated.evaluateExact(ExactPoint.of("1/4")));
    assertEquals(Rational.ZERO, separated.evaluateExact(ExactPoint.of("1/2")));
    assertEquals(Rational.of(1, 100), separated.evaluateExact(ExactPoint.of("3/4")));
    assertEquals(Rational.ZERO, separated.evaluateExact(ExactPoint.of("1/8")));
    assertEquals(
        0, separated.lipschitzBound(new DecimalArithmetic(8)).compareTo(new BigDecimal("8")));
    assertEquals(
        DecimalInterval.of("0", "1"), separated.range(Box.unit(1), new DecimalArithmetic(8)));
    assertEquals(
        DecimalInterval.of("0", "0"),
        separated.range(Box.of(DecimalInterval.of("0.45", "0.55")), new DecimalArithmetic(8)));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 8, 34})
  void boundsContinuousProfilesAndRepresentativeErrorsByExactEvaluation(int precision) {
    DecimalArithmetic arithmetic = new DecimalArithmetic(precision);
    MultiPeakMembership profile =
        new MultiPeakMembership(
            List.of(
                new TentPeak(ExactPoint.of("1/3", "2/3"), Rational.of(2, 7), Rational.of(2, 3)),
                new TentPeak(ExactPoint.of("3/4", "1/4"), Rational.of(1, 5), Rational.ONE)));
    for (int start = 0; start < 9; start++) {
      Box cell =
          Box.of(
              new DecimalInterval(
                  BigDecimal.valueOf(start, 1),
                  BigDecimal.valueOf(start + 2, 1).min(BigDecimal.ONE)),
              DecimalInterval.of("0.2", "0.8"));
      DecimalPoint representative = cell.midpoint();
      MembershipSample sample = MembershipSample.enclose(profile, cell, representative, arithmetic);
      Rational exactRepresentative = profile.evaluateExact(representative.toExact());
      Rational stored = Rational.fromDecimal(sample.grade());
      assertTrue(stored.compareTo(exactRepresentative) <= 0);
      assertTrue(
          exactRepresentative.subtract(stored).compareTo(Rational.fromDecimal(sample.gradeError()))
              <= 0);
      DecimalInterval range = profile.range(cell, arithmetic);
      for (int i = 0; i <= 8; i++) {
        Rational x =
            Rational.fromDecimal(cell.axis(0).lower())
                .add(
                    Rational.fromDecimal(cell.axis(0).upper().subtract(cell.axis(0).lower()))
                        .multiply(Rational.of(i, 8)));
        Rational y = Rational.of(1, 5).add(Rational.of(3, 5).multiply(Rational.of(i, 8)));
        ExactPoint point = new ExactPoint(List.of(x, y));
        Rational actual = profile.evaluateExact(point);
        assertTrue(range.contains(actual));
        assertTrue(actual.compareTo(Rational.fromDecimal(sample.upperMembership())) <= 0);
        assertTrue(
            actual
                    .subtract(exactRepresentative)
                    .abs()
                    .compareTo(Rational.fromDecimal(sample.variation()))
                <= 0);
        assertTrue(
            actual
                    .subtract(exactRepresentative)
                    .abs()
                    .compareTo(
                        Rational.fromDecimal(profile.lipschitzBound(arithmetic))
                            .multiply(point.distanceInfinity(representative.toExact())))
                <= 0);
      }
    }
  }

  @Test
  void rejectsInvalidProfilesAndInconsistentSampleBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TentPeak(ExactPoint.of("0.5"), Rational.ZERO, Rational.ONE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TentPeak(ExactPoint.of("0.5"), Rational.ONE, Rational.of(2)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TentPeak(ExactPoint.of("0.5"), Rational.ONE, Rational.of(-1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TentPeak(ExactPoint.of("-0.1"), Rational.ONE, Rational.ONE));
    assertThrows(IllegalArgumentException.class, () -> new MultiPeakMembership(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MultiPeakMembership(
                List.of(
                    new TentPeak(ExactPoint.of("0.5"), Rational.ONE, Rational.ONE),
                    new TentPeak(ExactPoint.of("0.5", "0.5"), Rational.ONE, Rational.ONE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            MembershipSample.enclose(
                separated,
                Box.of(DecimalInterval.of("0", "0.2")),
                DecimalPoint.of("0.5"),
                new DecimalArithmetic(8)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MembershipSample(
                new BigDecimal("-0.1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MembershipSample(
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.5")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MembershipSample(
                BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ONE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MembershipSample(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ONE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MembershipSample(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.1")));
  }
}
