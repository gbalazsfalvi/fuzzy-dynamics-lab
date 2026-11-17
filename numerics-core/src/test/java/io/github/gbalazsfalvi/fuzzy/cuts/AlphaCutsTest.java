package io.github.gbalazsfalvi.fuzzy.cuts;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlphaCutsTest {
  private final MultiPeakMembership profile =
      new MultiPeakMembership(
          List.of(
              new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.ONE),
              new TentPeak(ExactPoint.of("3/4"), Rational.of(1, 8), Rational.of(1, 100))));

  @Test
  void preservesSeparatedWeakCutsSingletonCoresAndTruncatedSupports() {
    assertEquals(2, ExactAlphaCuts.initial(profile, Rational.of(1, 200)).intervals().size());
    assertEquals(
        List.of(CutGeometryTest.interval("1/4", "1/4")),
        ExactAlphaCuts.initial(profile, Rational.ONE).intervals());
    assertEquals(2, ExactAlphaCuts.initial(profile, Rational.of(1, 100)).intervals().size());
    assertFalse(ExactAlphaCuts.initial(profile, Rational.of(1, 200)).contains(Rational.of(1, 2)));
    TentPeak boundary = new TentPeak(ExactPoint.of("0"), Rational.ONE, Rational.of(1, 2));
    assertEquals(
        List.of(CutGeometryTest.interval("0", "1/2")),
        ExactAlphaCuts.initial(boundary, Rational.of(1, 4)).intervals());
    assertTrue(ExactAlphaCuts.initial(boundary, Rational.ONE).intervals().isEmpty());
    assertTrue(
        ExactAlphaCuts.initial(
                new TentPeak(ExactPoint.of("1/2"), Rational.ONE, Rational.ZERO),
                Rational.of(1, 100))
            .intervals()
            .isEmpty());
  }

  @Test
  void computesTurningPointImagesAndCountsTheActualExactEvaluations() {
    IntervalUnion full = new IntervalUnion(List.of(CutGeometryTest.interval("0", "1")));
    ExactAlphaCuts.Image logistic =
        ExactAlphaCuts.image(new LogisticMap(Rational.of(37, 10)), full);
    assertEquals(List.of(CutGeometryTest.interval("0", "37/40")), logistic.cuts().intervals());
    assertEquals(3, logistic.exactPointEvaluations());
    assertEquals(1, logistic.intervalImages());
    assertEquals(
        List.of(CutGeometryTest.interval("1/2", "1")),
        ExactAlphaCuts.image(
                new TentMap(), new IntervalUnion(List.of(CutGeometryTest.interval("1/4", "3/4"))))
            .cuts()
            .intervals());
    assertEquals(
        2,
        ExactAlphaCuts.image(new AffineContraction(Rational.of(3, 4)), full)
            .exactPointEvaluations());
    assertEquals(
        2,
        ExactAlphaCuts.image(
                new TentMap(), new IntervalUnion(List.of(CutGeometryTest.interval("0", "1/4"))))
            .exactPointEvaluations());
    assertEquals(
        0,
        ExactAlphaCuts.image(new TentMap(), new IntervalUnion(List.of())).exactPointEvaluations());
  }

  @Test
  void transportsFullCutsAcrossEverySupportedScalarFamily() {
    for (CertifiedMap map :
        List.of(
            new TentMap(),
            new LogisticMap(Rational.of(37, 10)),
            new LogisticMap(Rational.ZERO),
            new AffineContraction(Rational.of(3, 4)))) {
      CaeProblem problem = new CaeProblem(map, profile, 3);
      AlphaCutTrajectory trajectory =
          AlphaCutTrajectory.compute(problem, AlphaCutTrajectory.uniformLevels(20));
      assertEquals(Rational.of(1, 20), trajectory.errorBound());
      assertEquals(4, trajectory.states().size());
      assertTrue(trajectory.exactPointEvaluations() > 0);
      assertTrue(trajectory.intervalImages() > 0);
      for (int level = 0; level < trajectory.levels().size(); level++) {
        Rational alpha = trajectory.levels().get(level);
        for (int probe = 0; probe <= 32; probe++) {
          ExactPoint point = new ExactPoint(List.of(Rational.of(probe, 32)));
          if (profile.evaluateExact(point).compareTo(alpha) >= 0) {
            for (int time = 0; time <= problem.horizon(); time++) {
              assertTrue(trajectory.states().get(time).get(level).contains(point.coordinate(0)));
              point = map.evaluateExact(point);
            }
          }
        }
      }
    }
    AlphaCutTrajectory uneven =
        new AlphaCutTrajectory(
            List.of(Rational.of(1, 4), Rational.ONE),
            List.of(List.of(new IntervalUnion(List.of()), new IntervalUnion(List.of()))),
            0,
            0);
    assertEquals(Rational.of(3, 4), uneven.errorBound());
  }

  @Test
  void rejectsUnsupportedDimensionsInvalidLevelsAndInconsistentEvidence() {
    TentPeak two = new TentPeak(ExactPoint.of("1/2", "1/2"), Rational.ONE, Rational.ONE);
    assertThrows(IllegalArgumentException.class, () -> ExactAlphaCuts.initial(two, Rational.ONE));
    for (Rational alpha : List.of(Rational.ZERO, Rational.of(-1), Rational.of(2))) {
      assertThrows(IllegalArgumentException.class, () -> ExactAlphaCuts.initial(profile, alpha));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ExactAlphaCuts.image(
                new CoupledTentMap(Rational.of(1, 4)), new IntervalUnion(List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AlphaCutTrajectory.compute(
                new CaeProblem(new CoupledTentMap(Rational.of(1, 4)), two, 0),
                List.of(Rational.ONE)));
    for (List<Rational> levels :
        List.of(
            List.<Rational>of(),
            List.of(Rational.of(1, 2)),
            List.of(Rational.ZERO, Rational.ONE),
            List.of(Rational.ONE, Rational.ONE),
            List.of(Rational.of(2), Rational.ONE))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> AlphaCutTrajectory.compute(new CaeProblem(new TentMap(), profile, 0), levels));
    }
    assertThrows(IllegalArgumentException.class, () -> AlphaCutTrajectory.uniformLevels(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AlphaCutTrajectory(List.of(Rational.ONE), List.of(), 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AlphaCutTrajectory(List.of(Rational.ONE), List.of(List.of()), 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AlphaCutTrajectory(
                List.of(Rational.ONE), List.of(List.of(new IntervalUnion(List.of()))), -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AlphaCutTrajectory(
                List.of(Rational.ONE), List.of(List.of(new IntervalUnion(List.of()))), 0, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExactAlphaCuts.Image(new IntervalUnion(List.of()), -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExactAlphaCuts.Image(new IntervalUnion(List.of()), 0, -1));
  }
}
