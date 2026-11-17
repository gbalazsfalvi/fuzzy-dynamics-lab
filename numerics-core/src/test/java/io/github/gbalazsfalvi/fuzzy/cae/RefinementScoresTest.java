package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefinementScoresTest {
  @Test
  void everyPolicyPreservesTheSameCertificateAndPeriodicFairnessContract() {
    CaeProblem problem =
        new CaeProblem(
            new LogisticMap(Rational.of(37, 10)),
            new TentPeak(ExactPoint.of("2/5"), Rational.of(1, 5), Rational.ONE),
            5);
    List<List<CellPath>> paths = new ArrayList<>();
    for (CaeSettings.Strategy strategy :
        java.util.Arrays.stream(CaeSettings.Strategy.values())
            .filter(value -> value != CaeSettings.Strategy.RANKED)
            .toList()) {
      CaeSettings settings =
          new CaeSettings(
              new DecimalArithmetic(16), new BigDecimal("0.001"), 18, 19, 12, 5, strategy);
      CaeResult result = new CaeSolver().solve(problem, settings);
      assertEquals(result, new CaeSolver().solve(problem, settings));
      assertTrue(
          new CertificateVerifier().verify(problem, settings, result.best().orElseThrow()).valid());
      assertTrue(
          new CertificateVerifier().verify(problem, settings, result.last().orElseThrow()).valid());
      for (RefinementEvent event : result.history()) {
        if (strategy != CaeSettings.Strategy.UNIFORM && event.refinement() % 5 == 0) {
          assertEquals(RefinementEvent.Selection.FAIRNESS, event.selection());
        }
      }
      paths.add(result.history().stream().map(RefinementEvent::parent).toList());
    }
    assertTrue(
        paths.stream().distinct().count() >= 3, "The baselines must exercise different choices.");
  }

  @Test
  void scoringHandlesZeroHorizonsAndZeroPredictedGainWithoutAffectingBounds() {
    CaeProblem problem =
        new CaeProblem(
            new LogisticMap(Rational.of(37, 10)),
            new TentPeak(ExactPoint.of("1/5"), Rational.of(1, 20), Rational.of(1, 100)),
            0);
    for (CaeSettings.Strategy strategy :
        java.util.Arrays.stream(CaeSettings.Strategy.values())
            .filter(value -> value != CaeSettings.Strategy.RANKED)
            .toList()) {
      CaeSettings settings =
          new CaeSettings(new DecimalArithmetic(16), new BigDecimal("0.001"), 1, 2, 4, 5, strategy);
      CaeCell root =
          CaeCellEvaluator.evaluate(
              problem, settings, CellPath.root(), new CaeControl(() -> false));
      assertTrue(RefinementScores.score(root, settings).signum() >= 0);
      if (strategy == CaeSettings.Strategy.PREDICTED_GAIN_PER_WORK) {
        assertEquals(0, RefinementScores.score(root, settings).signum());
      }
      CaeResult result = new CaeSolver().solve(problem, settings);
      assertTrue(
          new CertificateVerifier().verify(problem, settings, result.last().orElseThrow()).valid());
    }
  }
}
