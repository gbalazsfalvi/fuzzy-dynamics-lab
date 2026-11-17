package io.github.gbalazsfalvi.fuzzy.cuts;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.EndographMetric;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AlphaGridEndographTest {
  @Test
  void matchesTheIndependentFiniteMetricOnExactGridValuedStates() {
    Random random = new Random(17112026L);
    List<Rational> levels = AlphaCutTrajectory.uniformLevels(8);
    for (int trial = 0; trial < 80; trial++) {
      FiniteFuzzySet target = randomState(random);
      FiniteFuzzySet source = randomState(random);
      List<IntervalUnion> cuts =
          levels.stream()
              .map(
                  alpha ->
                      new IntervalUnion(
                          target.alphaCut(alpha).stream()
                              .map(
                                  point ->
                                      new RationalInterval(
                                          point.coordinate(0), point.coordinate(0)))
                              .toList()))
              .toList();
      AlphaCutTrajectory reference = new AlphaCutTrajectory(levels, List.of(cuts), 0, 0);
      assertEquals(
          EndographMetric.distance(source, target),
          AlphaGridEndograph.distance(source, reference, 0));
    }
  }

  @Test
  void computesContinuousIntervalCoverageAndBothCommonBaselineDirections() {
    List<Rational> levels = List.of(Rational.of(1, 5), Rational.ONE);
    AlphaCutTrajectory reference =
        new AlphaCutTrajectory(
            levels,
            List.of(
                List.of(
                    new IntervalUnion(List.of(CutGeometryTest.interval("0", "1"))),
                    new IntervalUnion(List.of()))),
            0,
            0);
    FiniteFuzzySet low = state("1/2", "1/5");
    assertEquals(Rational.of(1, 5), AlphaGridEndograph.distance(low, reference, 0));
    assertEquals(Rational.of(4, 5), AlphaGridEndograph.distance(state("1/2", "1"), reference, 0));
    assertEquals(
        Rational.of(1, 5),
        AlphaGridEndograph.distance(new FiniteFuzzySet(1, List.of()), reference, 0));
    AlphaCutTrajectory zero =
        new AlphaCutTrajectory(
            List.of(Rational.ONE), List.of(List.of(new IntervalUnion(List.of()))), 0, 0);
    assertEquals(Rational.of(1, 5), AlphaGridEndograph.distance(low, zero, 0));
    assertThrows(
        IllegalArgumentException.class, () -> AlphaGridEndograph.distance(low, reference, -1));
    assertThrows(
        IllegalArgumentException.class, () -> AlphaGridEndograph.distance(low, reference, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> AlphaGridEndograph.distance(new FiniteFuzzySet(2, List.of()), reference, 0));
  }

  private static FiniteFuzzySet randomState(Random random) {
    List<FuzzyAtom> atoms = new ArrayList<>();
    for (int index = 0; index < random.nextInt(7); index++) {
      atoms.add(
          new FuzzyAtom(
              new ExactPoint(List.of(Rational.of(random.nextInt(17), 16))),
              Rational.of(random.nextInt(9), 8)));
    }
    return new FiniteFuzzySet(1, atoms);
  }

  private static FiniteFuzzySet state(String point, String grade) {
    return new FiniteFuzzySet(
        1, List.of(new FuzzyAtom(ExactPoint.of(point), Rational.parse(grade))));
  }
}
