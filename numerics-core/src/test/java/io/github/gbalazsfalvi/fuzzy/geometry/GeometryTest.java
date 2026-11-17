package io.github.gbalazsfalvi.fuzzy.geometry;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryTest {
  @Test
  void copiesCoordinatesAndIgnoresDecimalScale() {
    List<Rational> coordinates = new ArrayList<>(List.of(Rational.ZERO));
    ExactPoint point = new ExactPoint(coordinates);
    coordinates.set(0, Rational.ONE);
    assertEquals(ExactPoint.of("0"), point);
    assertThrows(UnsupportedOperationException.class, () -> point.coordinates().add(Rational.ONE));
    assertEquals(DecimalPoint.of("0.1", "0.5"), DecimalPoint.of("0.10", "0.500"));
    assertEquals(ExactPoint.of("1/10", "1/2"), DecimalPoint.of("0.1", "0.5").toExact());
    assertEquals(2, DecimalPoint.of("0.1", "0.5").dimension());
    assertEquals(
        Rational.of(3, 4), ExactPoint.of("0", "1/4").distanceInfinity(ExactPoint.of("1/2", "1")));
    assertTrue(ExactPoint.of("0").compareTo(ExactPoint.of("0", "0")) < 0);
    assertTrue(ExactPoint.of("1/4").compareTo(ExactPoint.of("1/2")) < 0);
    assertEquals(0, ExactPoint.of("1/2").compareTo(ExactPoint.of("2/4")));
  }

  @Test
  void splitsTheLongestAxisWithoutLosingTheBoundary() {
    Box parent = Box.of(DecimalInterval.of("0", "0.2"), DecimalInterval.of("0", "1"));
    List<Box> children = parent.bisectLongest();
    assertEquals(DecimalInterval.of("0", "0.5"), children.getFirst().axis(1));
    assertEquals(DecimalInterval.of("0.5", "1"), children.getLast().axis(1));
    assertTrue(children.stream().allMatch(parent::contains));
    assertTrue(children.stream().allMatch(child -> child.contains(DecimalPoint.of("0.1", "0.5"))));
    assertEquals(
        Box.of(DecimalInterval.of("0", "0.2"), DecimalInterval.of("0.5", "0.5")),
        children.getFirst().intersection(children.getLast()).orElseThrow());
    for (int i = 0; i <= 100; i++) {
      ExactPoint point = new ExactPoint(List.of(Rational.of(1, 10), Rational.of(i, 100)));
      assertTrue(children.stream().anyMatch(child -> child.contains(point)));
    }
    assertEquals(DecimalPoint.of("0.1", "0.5"), parent.midpoint());
    assertEquals(
        0,
        parent
            .radiusAround(parent.midpoint(), new DecimalArithmetic(5))
            .compareTo(new BigDecimal("0.5")));
    assertFalse(parent.contains(ExactPoint.of("1", "1")));
    assertFalse(parent.contains(Box.unit(2)));
    assertTrue(
        parent
            .intersection(Box.of(DecimalInterval.of("0.5", "1"), DecimalInterval.of("0", "1")))
            .isEmpty());
  }

  @Test
  void rejectsEmptyGeometryDimensionMismatchesAndInvalidDomains() {
    assertThrows(IllegalArgumentException.class, () -> new ExactPoint(List.of()));
    assertThrows(IllegalArgumentException.class, () -> new DecimalPoint(List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Box(List.of()));
    assertThrows(IllegalArgumentException.class, () -> Box.unit(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExactPoint.of("0").distanceInfinity(ExactPoint.of("0", "0")));
    assertThrows(
        IllegalArgumentException.class, () -> Box.unit(1).contains(ExactPoint.of("0", "0")));
    assertThrows(IllegalArgumentException.class, () -> Box.unit(1).contains(Box.unit(2)));
    assertThrows(IllegalArgumentException.class, () -> Box.unit(1).intersection(Box.unit(2)));
    assertThrows(
        IllegalStateException.class, () -> Box.point(DecimalPoint.of("0")).bisectLongest());
    assertThrows(
        IllegalArgumentException.class,
        () -> UnitDomain.require(Box.of(DecimalInterval.of("-0.1", "0.5")), 1));
    assertThrows(IllegalArgumentException.class, () -> UnitDomain.require(Box.unit(2), 1));
    assertThrows(
        IllegalArgumentException.class, () -> UnitDomain.require(ExactPoint.of("1.001"), 1));
    assertThrows(
        IllegalArgumentException.class, () -> UnitDomain.require(ExactPoint.of("-0.001"), 1));
    assertThrows(
        IllegalArgumentException.class, () -> UnitDomain.require(ExactPoint.of("0", "0"), 1));
  }
}
