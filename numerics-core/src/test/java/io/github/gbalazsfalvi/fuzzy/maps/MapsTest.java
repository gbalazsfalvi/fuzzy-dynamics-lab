package io.github.gbalazsfalvi.fuzzy.maps;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MapsTest {
  static Stream<Arguments> mapsAndPrecisions() {
    return maps().stream().flatMap(map -> Stream.of(2, 8, 34).map(p -> Arguments.of(map, p)));
  }

  static List<CertifiedMap> maps() {
    return List.of(
        new AffineContraction(Rational.ZERO),
        new AffineContraction(Rational.of(3, 4)),
        new AffineContraction(Rational.of(1, 3)),
        new TentMap(),
        new LogisticMap(Rational.ZERO),
        new LogisticMap(Rational.of(37, 10)),
        new LogisticMap(Rational.of(4)),
        new CoupledTentMap(Rational.ZERO),
        new CoupledTentMap(Rational.of(1, 3)),
        new CoupledTentMap(Rational.ONE));
  }

  @ParameterizedTest
  @MethodSource("mapsAndPrecisions")
  void intervalImagesAndLipschitzBoundsContainIndependentExactReferences(
      CertifiedMap map, int precision) {
    DecimalArithmetic arithmetic = new DecimalArithmetic(precision);
    Random random = new Random(9017);
    for (int trial = 0; trial < 60; trial++) {
      List<DecimalInterval> axes = new ArrayList<>();
      List<Rational> left = new ArrayList<>();
      List<Rational> right = new ArrayList<>();
      for (int axis = 0; axis < map.dimension(); axis++) {
        int lower = random.nextInt(99);
        int upper = lower + 1 + random.nextInt(100 - lower);
        DecimalInterval interval =
            new DecimalInterval(BigDecimal.valueOf(lower, 2), BigDecimal.valueOf(upper, 2));
        axes.add(interval);
        left.add(Rational.of(lower, 100));
        right.add(Rational.of(upper, 100));
      }
      Box input = new Box(axes);
      Box image = map.image(input, arithmetic);
      assertTrue(Box.unit(map.dimension()).contains(image));
      ExactPoint first = new ExactPoint(left);
      ExactPoint last = new ExactPoint(right);
      Rational bound = Rational.fromDecimal(map.lipschitzBound(input, arithmetic));
      assertTrue(
          map.evaluateExact(first)
                  .distanceInfinity(map.evaluateExact(last))
                  .compareTo(bound.multiply(first.distanceInfinity(last)))
              <= 0);
      for (int part = 0; part <= 4; part++) {
        List<Rational> coordinates = new ArrayList<>();
        for (int axis = 0; axis < map.dimension(); axis++) {
          Rational fraction = Rational.of((part + axis) % 5, 4);
          coordinates.add(
              left.get(axis).add(right.get(axis).subtract(left.get(axis)).multiply(fraction)));
        }
        assertTrue(image.contains(map.evaluateExact(new ExactPoint(coordinates))), map.id());
      }
    }
  }

  @Test
  void includesTurningPointsAndKnownClosedRanges() {
    DecimalArithmetic arithmetic = new DecimalArithmetic(34);
    assertEquals(
        Box.of(DecimalInterval.of("0.64", "1")),
        new LogisticMap(Rational.of(4))
            .image(Box.of(DecimalInterval.of("0.2", "0.8")), arithmetic));
    assertEquals(
        Box.of(DecimalInterval.of("0", "1")),
        new LogisticMap(Rational.of(4)).image(Box.unit(1), arithmetic));
    assertEquals(
        Box.of(DecimalInterval.of("0.6", "1")),
        new TentMap().image(Box.of(DecimalInterval.of("0.4", "0.7")), arithmetic));
    assertEquals(
        Box.of(DecimalInterval.of("0.25", "0.375")),
        new AffineContraction(Rational.of(1, 2))
            .image(Box.of(DecimalInterval.of("0", "0.25")), arithmetic));
    assertEquals(
        Box.of(DecimalInterval.of("0.2", "0.45"), DecimalInterval.of("0.2", "0.55")),
        new CoupledTentMap(Rational.of(1, 4))
            .image(
                Box.of(DecimalInterval.of("0.1", "0.2"), DecimalInterval.of("0.7", "0.9")),
                arithmetic));
    assertEquals(ExactPoint.of("1"), new TentMap().evaluateExact(ExactPoint.of("1/2")));
    assertEquals(
        ExactPoint.of("1"), new LogisticMap(Rational.of(4)).evaluateExact(ExactPoint.of("1/2")));
    assertEquals(
        0,
        new LogisticMap(Rational.of(4))
            .lipschitzBound(Box.of(DecimalInterval.of("0.49", "0.51")), arithmetic)
            .compareTo(new BigDecimal("0.08")));
  }

  @Test
  void rejectsUnsupportedParametersAndWholeBoxesOutsideTheDomain() {
    assertThrows(IllegalArgumentException.class, () -> new AffineContraction(Rational.ONE));
    assertThrows(IllegalArgumentException.class, () -> new AffineContraction(Rational.of(-1)));
    assertThrows(IllegalArgumentException.class, () -> new LogisticMap(Rational.of(41, 10)));
    assertThrows(IllegalArgumentException.class, () -> new LogisticMap(Rational.of(-1)));
    assertThrows(IllegalArgumentException.class, () -> new CoupledTentMap(Rational.of(11, 10)));
    assertThrows(IllegalArgumentException.class, () -> new CoupledTentMap(Rational.of(-1, 10)));
    for (CertifiedMap map : maps()) {
      Box wrongDimension = Box.unit(map.dimension() + 1);
      assertThrows(
          IllegalArgumentException.class,
          () -> map.image(wrongDimension, new DecimalArithmetic(8)));
      assertThrows(
          IllegalArgumentException.class,
          () -> map.lipschitzBound(wrongDimension, new DecimalArithmetic(8)));
      assertThrows(IllegalArgumentException.class, () -> map.evaluateExact(ExactPoint.of("-1")));
    }
    Box crossing = Box.of(DecimalInterval.of("-0.01", "0.5"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TentMap().image(crossing, new DecimalArithmetic(8)));
  }
}
