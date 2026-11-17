package io.github.gbalazsfalvi.fuzzy.cuts;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.List;
import org.junit.jupiter.api.Test;

class CutGeometryTest {
  static RationalInterval interval(String left, String right) {
    return new RationalInterval(Rational.parse(left), Rational.parse(right));
  }

  @Test
  void canonicalizesTouchingAndOverlappingIntervalsWithoutFillingGaps() {
    IntervalUnion union =
        new IntervalUnion(
            List.of(
                interval("3/4", "1"),
                interval("0", "1/4"),
                interval("1/4", "1/3"),
                interval("1/8", "1/5")));
    assertEquals(List.of(interval("0", "1/3"), interval("3/4", "1")), union.intervals());
    assertTrue(union.contains(Rational.of(1, 3)));
    assertFalse(union.contains(Rational.of(1, 2)));
    assertFalse(union.contains(Rational.of(-1)));
    assertEquals(Rational.ZERO, union.distance(Rational.of(1, 4)));
    assertEquals(Rational.of(1, 6), union.distance(Rational.of(1, 2)));
    assertEquals(Rational.ONE, union.distance(Rational.of(-1)));
    assertThrows(UnsupportedOperationException.class, () -> union.intervals().clear());
  }

  @Test
  void includesInteriorGapPeaksInTheExactHausdorffDistance() {
    IntervalUnion continuous = new IntervalUnion(List.of(interval("1/5", "4/5")));
    IntervalUnion endpoints =
        new IntervalUnion(List.of(interval("1/5", "1/5"), interval("4/5", "4/5")));
    assertEquals(Rational.of(3, 10), continuous.hausdorff(endpoints));
    assertEquals(Rational.ZERO, endpoints.directedTo(continuous));
    assertEquals(Rational.of(3, 10), continuous.directedTo(endpoints));
    assertEquals(Rational.ZERO, continuous.hausdorff(continuous));
    IntervalUnion singleton = new IntervalUnion(List.of(interval("1/2", "1/2")));
    assertEquals(Rational.of(3, 10), singleton.hausdorff(endpoints));
  }

  @Test
  void keepsEmptyCutStatusesDistinctAndRejectsUndefinedDistances() {
    IntervalUnion empty = new IntervalUnion(List.of());
    IntervalUnion nonempty = new IntervalUnion(List.of(interval("0", "1")));
    assertEquals(CutComparison.Status.BOTH_EMPTY, CutComparison.compare(empty, empty).status());
    assertEquals(
        CutComparison.Status.REFERENCE_EMPTY, CutComparison.compare(empty, nonempty).status());
    assertEquals(
        CutComparison.Status.APPROXIMATION_EMPTY, CutComparison.compare(nonempty, empty).status());
    assertNull(CutComparison.compare(nonempty, empty).distance());
    assertEquals(Rational.ZERO, CutComparison.compare(nonempty, nonempty).distance());
    assertEquals(Rational.ZERO, empty.directedTo(nonempty));
    assertThrows(IllegalStateException.class, () -> empty.distance(Rational.ZERO));
    assertThrows(IllegalStateException.class, () -> nonempty.hausdorff(empty));
    assertThrows(IllegalStateException.class, () -> empty.hausdorff(nonempty));
    assertThrows(IllegalStateException.class, () -> nonempty.directedTo(empty));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CutComparison(CutComparison.Status.NONEMPTY, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CutComparison(CutComparison.Status.BOTH_EMPTY, Rational.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CutComparison(CutComparison.Status.NONEMPTY, Rational.of(-1)));
  }

  @Test
  void validatesCompleteIntervalDomains() {
    assertThrows(IllegalArgumentException.class, () -> interval("-1/10", "1/2"));
    assertThrows(IllegalArgumentException.class, () -> interval("0", "11/10"));
    assertThrows(IllegalArgumentException.class, () -> interval("3/4", "1/4"));
    assertEquals(Rational.of(1, 2), interval("1/2", "1/2").distance(Rational.ZERO));
  }
}
