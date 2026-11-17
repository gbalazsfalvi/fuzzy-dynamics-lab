package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.EndographMetric;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import io.github.gbalazsfalvi.fuzzy.trajectory.Trajectories;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CaeReferenceTest {
  @ParameterizedTest
  @ValueSource(ints = {2, 8, 34})
  void boundsExactFiniteReferencesEnrichedWithEveryRepresentative(int precision) {
    for (CertifiedMap map :
        List.of(
            new AffineContraction(Rational.of(3, 4)), new TentMap(),
            new LogisticMap(Rational.of(37, 10)), new CoupledTentMap(Rational.of(1, 3)))) {
      boolean one = map.dimension() == 1;
      MultiPeakMembership profile =
          new MultiPeakMembership(
              List.of(
                  new TentPeak(
                      one ? ExactPoint.of("1/4") : ExactPoint.of("1/4", "3/4"),
                      Rational.of(1, 4),
                      Rational.ONE),
                  new TentPeak(
                      one ? ExactPoint.of("3/4") : ExactPoint.of("3/4", "1/4"),
                      Rational.of(1, 8),
                      Rational.of(1, 100))));
      CaeProblem problem = new CaeProblem(map, profile, 3);
      CaeSettings settings =
          new CaeSettings(
              new DecimalArithmetic(precision),
              new BigDecimal("0.05"),
              24,
              25,
              12,
              5,
              CaeSettings.Strategy.MAXIMUM_BOUND);
      CaeResult result = new CaeSolver().solve(problem, settings);
      for (CaeSnapshot snapshot :
          List.of(result.best().orElseThrow(), result.last().orElseThrow())) {
        List<FuzzyAtom> initial = new ArrayList<>();
        for (int index = 0; index <= 16; index++) {
          ExactPoint point =
              one
                  ? new ExactPoint(List.of(Rational.of(index, 16)))
                  : new ExactPoint(List.of(Rational.of(index, 16), Rational.of(16 - index, 16)));
          initial.add(new FuzzyAtom(point, profile.evaluateExact(point)));
        }
        for (CaeCell cell : snapshot.cells()) {
          ExactPoint center = cell.initial().midpoint().toExact();
          initial.add(new FuzzyAtom(center, profile.evaluateExact(center)));
          List<ExactPoint> exact = Trajectories.exact(map, center, problem.horizon());
          for (int time = 0; time <= problem.horizon(); time++) {
            CaeStep step = cell.trajectory().get(time);
            assertTrue(step.reachable().contains(exact.get(time)));
            assertTrue(
                exact
                        .get(time)
                        .distanceInfinity(step.representative().toExact())
                        .compareTo(Rational.fromDecimal(step.radius()))
                    <= 0);
            if (time > 0) {
              CaeStep previous = cell.trajectory().get(time - 1);
              Rational residual =
                  map.evaluateExact(previous.representative().toExact())
                      .distanceInfinity(step.representative().toExact());
              assertTrue(residual.compareTo(Rational.fromDecimal(step.evaluationResidual())) <= 0);
              Rational recurrence =
                  Rational.fromDecimal(step.transitionLipschitz())
                      .multiply(Rational.fromDecimal(previous.radius()))
                      .add(Rational.fromDecimal(step.evaluationResidual()));
              assertTrue(recurrence.compareTo(Rational.fromDecimal(step.propagatedRadius())) <= 0);
              assertTrue(step.radius().compareTo(step.directRadius()) <= 0);
              assertTrue(step.radius().compareTo(step.propagatedRadius()) <= 0);
            }
          }
        }
        FiniteFuzzySet reference = new FiniteFuzzySet(map.dimension(), initial);
        for (int time = 0; time <= problem.horizon(); time++) {
          Rational measured = EndographMetric.distance(reference, snapshot.approximation(time));
          assertTrue(
              measured.compareTo(Rational.fromDecimal(snapshot.bounds().get(time))) <= 0,
              map.id() + " at time " + time + " with precision " + precision);
          reference = reference.extend(map);
        }
      }
    }
  }

  @Test
  void fixedPrecisionCanExhaustDepthWithoutFalselyCertifying() {
    CaeProblem problem =
        new CaeProblem(
            new LogisticMap(Rational.of(37, 10)),
            new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 7), Rational.of(2, 3)),
            2);
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(1),
            new BigDecimal("0.0001"),
            200,
            201,
            6,
            5,
            CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeResult result = new CaeSolver().solve(problem, settings);
    assertEquals(CaeResult.StopReason.MAX_DEPTH, result.reason());
    assertTrue(result.best().orElseThrow().worstBound().compareTo(settings.tolerance()) > 0);
    assertTrue(
        new CertificateVerifier().verify(problem, settings, result.last().orElseThrow()).valid());
  }
}
