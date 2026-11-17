package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CaeContinuousReferenceTest {
  @Test
  void checksTheContinuousOracleAgainstAClosedFormAnswer() {
    Rational center = Rational.of(1, 2);
    Rational width = Rational.of(1, 4);
    FiniteFuzzySet top =
        new FiniteFuzzySet(
            1, List.of(new FuzzyAtom(new ExactPoint(List.of(center)), Rational.ONE)));
    // The nearest point on a slope of magnitude four solves r = 1 - 4r, hence r = 1/5.
    assertEquals(
        Rational.of(1, 5),
        ExactTriangleEndographReference.distance(center, width, Rational.ONE, top));
    assertEquals(
        Rational.ONE,
        ExactTriangleEndographReference.distance(
            center, width, Rational.ONE, new FiniteFuzzySet(1, List.of())));
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 12, 34})
  void enclosesTheExactContinuousEndographErrorThroughoutAnAffineTrajectory(int precision) {
    Rational contraction = Rational.of(3, 4);
    Rational center = Rational.of(1, 2);
    Rational initialWidth = Rational.of(1, 4);
    Rational height = Rational.of(2, 3);
    CaeProblem problem =
        new CaeProblem(
            new AffineContraction(contraction),
            new TentPeak(new ExactPoint(List.of(center)), initialWidth, height),
            4);
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(precision),
            new BigDecimal("0.001"),
            11,
            12,
            8,
            5,
            CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeResult result = new CaeSolver().solve(problem, settings);
    for (CaeSnapshot snapshot : List.of(result.best().orElseThrow(), result.last().orElseThrow())) {
      for (int time = 0; time <= problem.horizon(); time++) {
        Rational width = initialWidth.multiply(contraction.pow(time));
        Rational error =
            ExactTriangleEndographReference.distance(
                center, width, height, snapshot.approximation(time));
        assertTrue(
            error.compareTo(Rational.fromDecimal(snapshot.bounds().get(time))) <= 0,
            () -> "Exact continuous error " + error + " exceeded the certificate.");
      }
    }
  }

  @Test
  void splittingCanIncreaseTheActualErrorAndNotJustItsUpperBound() {
    CaeProblem problem =
        new CaeProblem(
            new AffineContraction(Rational.of(3, 4)),
            new MultiPeakMembership(
                List.of(
                    new TentPeak(ExactPoint.of("1/2"), Rational.ONE, Rational.of(1, 2)),
                    new TentPeak(ExactPoint.of("1/10"), Rational.of(1, 20), Rational.ONE))),
            0);
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(34),
            new BigDecimal("0.1"),
            1,
            2,
            8,
            5,
            CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeResult result = new CaeSolver().solve(problem, settings);
    CaeSnapshot best = result.best().orElseThrow();
    CaeSnapshot last = result.last().orElseThrow();
    assertEquals(new BigDecimal("0.5"), best.worstBound());
    assertEquals(new BigDecimal("0.625"), last.worstBound());
    // At the narrow peak's top, these are exact lower witnesses for the two Hausdorff errors.
    // The continuous upper certificates match them, so both actual errors are determined exactly.
    Rational initialWitness =
        distanceFromTop(ExactPoint.of("1/10"), Rational.ONE, best.approximation(0));
    Rational splitWitness =
        distanceFromTop(ExactPoint.of("1/10"), Rational.ONE, last.approximation(0));
    assertEquals(Rational.fromDecimal(best.worstBound()), initialWitness);
    assertEquals(Rational.fromDecimal(last.worstBound()), splitWitness);
    assertTrue(splitWitness.compareTo(initialWitness) > 0);
  }

  private static Rational distanceFromTop(
      ExactPoint point, Rational height, FiniteFuzzySet target) {
    Rational nearest = height;
    for (FuzzyAtom atom : target.atoms()) {
      nearest =
          nearest.min(
              point
                  .distanceInfinity(atom.location())
                  .max(height.subtract(atom.grade()).max(Rational.ZERO)));
    }
    return nearest;
  }
}
