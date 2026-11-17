package io.github.gbalazsfalvi.fuzzy.trajectory;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrajectoriesTest {
  @Test
  void agreesWithTheIndependentAffineClosedForm() {
    AffineContraction map = new AffineContraction(Rational.of(3, 4));
    Rational initial = Rational.of(1, 7);
    List<ExactPoint> orbit = Trajectories.exact(map, new ExactPoint(List.of(initial)), 20);
    for (int time = 0; time <= 20; time++) {
      assertEquals(
          Trajectories.affineClosedForm(map, initial, time), orbit.get(time).coordinate(0));
    }
    assertEquals(21, orbit.size());
    assertThrows(UnsupportedOperationException.class, () -> orbit.clear());
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 8, 34})
  void enclosesExactTrajectoriesAndRepresentativeResiduals(int precision) {
    DecimalArithmetic arithmetic = new DecimalArithmetic(precision);
    for (CertifiedMap map :
        List.of(
            new AffineContraction(Rational.of(3, 4)),
            new TentMap(),
            new LogisticMap(Rational.of(37, 10)),
            new CoupledTentMap(Rational.of(1, 3)))) {
      Box initial =
          map.dimension() == 1
              ? Box.of(DecimalInterval.of("0.12", "0.27"))
              : Box.of(DecimalInterval.of("0.12", "0.27"), DecimalInterval.of("0.63", "0.78"));
      List<TrajectoryStep> enclosure = Trajectories.enclose(map, initial, 6, arithmetic);
      assertEquals(7, enclosure.size());
      for (int sample = 0; sample <= 6; sample++) {
        Rational x =
            Rational.of(12, 100).add(Rational.of(15, 100).multiply(Rational.of(sample, 6)));
        ExactPoint first =
            map.dimension() == 1
                ? new ExactPoint(List.of(x))
                : new ExactPoint(
                    List.of(
                        x,
                        Rational.of(63, 100)
                            .add(Rational.of(15, 100).multiply(Rational.of(sample, 6)))));
        List<ExactPoint> exact = Trajectories.exact(map, first, 6);
        for (int time = 0; time <= 6; time++) {
          TrajectoryStep step = enclosure.get(time);
          assertTrue(step.reachable().contains(exact.get(time)), map.id() + " time " + time);
          Rational actualRadius = exact.get(time).distanceInfinity(step.representative().toExact());
          assertTrue(actualRadius.compareTo(Rational.fromDecimal(step.radius())) <= 0);
        }
      }
      for (int time = 1; time <= 6; time++) {
        TrajectoryStep previous = enclosure.get(time - 1);
        TrajectoryStep next = enclosure.get(time);
        Rational actualResidual =
            map.evaluateExact(previous.representative().toExact())
                .distanceInfinity(next.representative().toExact());
        assertTrue(actualResidual.compareTo(Rational.fromDecimal(next.evaluationResidual())) <= 0);
      }
    }
  }

  @Test
  void representsInitialOnlyRunsAndRejectsInvalidRequests() {
    TentMap map = new TentMap();
    DecimalArithmetic arithmetic = new DecimalArithmetic(8);
    assertEquals(1, Trajectories.enclose(map, Box.unit(1), 0, arithmetic).size());
    assertEquals(
        BigDecimal.ZERO,
        Trajectories.enclose(map, Box.unit(1), 0, arithmetic).getFirst().evaluationResidual());
    assertThrows(
        IllegalArgumentException.class, () -> Trajectories.exact(map, ExactPoint.of("0"), -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Trajectories.enclose(map, Box.unit(1), -1, arithmetic));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Trajectories.affineClosedForm(
                new AffineContraction(Rational.of(1, 2)), Rational.ONE, -1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TrajectoryStep(
                -1, Box.unit(1), DecimalPoint.of("0.5"), BigDecimal.ONE, BigDecimal.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TrajectoryStep(
                0, Box.unit(1), DecimalPoint.of("0.5", "0.5"), BigDecimal.ONE, BigDecimal.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TrajectoryStep(
                0, Box.unit(1), DecimalPoint.of("0.5"), BigDecimal.ONE.negate(), BigDecimal.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TrajectoryStep(
                0, Box.unit(1), DecimalPoint.of("0.5"), BigDecimal.ONE, BigDecimal.ONE.negate()));
  }
}
