package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RankedSolverTest {
  @Test
  void externalScoresCannotReplaceBoundsAndEveryFifthSplitRemainsFair() {
    CaeSettings original = CaeSolverTest.settings(18, 19, 12, CaeSettings.Strategy.RANKED);
    CellRanker ranker = cell -> BigDecimal.valueOf(-cell.path().depth());
    CaeResult first = new CaeSolver(ranker).solve(CaeSolverTest.problem(), original);
    assertEquals(first, new CaeSolver(ranker).solve(CaeSolverTest.problem(), original));
    assertTrue(
        new CertificateVerifier()
            .verify(CaeSolverTest.problem(), original, first.best().orElseThrow())
            .valid());
    assertTrue(
        new CertificateVerifier()
            .verify(CaeSolverTest.problem(), original, first.last().orElseThrow())
            .valid());
    for (RefinementEvent event : first.history()) {
      assertEquals(
          event.refinement() % 5 == 0
              ? RefinementEvent.Selection.FAIRNESS
              : RefinementEvent.Selection.RANKED,
          event.selection());
    }
  }

  @Test
  void aFailedOrNullSecondChildScoreCannotDestroyTheCompleteParentCover() {
    for (boolean returnNull : java.util.List.of(false, true)) {
      CellRanker ranker =
          cell -> {
            if (cell.path().bits().equals("1")) {
              if (returnNull) {
                return null;
              }
              throw new ArithmeticException("Injected second child scoring failure.");
            }
            return BigDecimal.ONE;
          };
      CaeSettings settings = CaeSolverTest.settings(8, 9, 8, CaeSettings.Strategy.RANKED);
      CaeResult result = new CaeSolver(ranker).solve(CaeSolverTest.problem(), settings);
      assertEquals(CaeResult.Status.NUMERICAL_FAILURE, result.status());
      assertEquals(1, result.last().orElseThrow().cells().size());
      assertEquals(0, result.history().size());
      assertEquals(3, result.work().cellAttempts());
      assertTrue(
          new CertificateVerifier()
              .verify(CaeSolverTest.problem(), settings, result.last().orElseThrow())
              .valid());
    }
  }

  @Test
  void rejectsMissingOrUnexpectedRankersAtTheTypedExecutionBoundary() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeSolver()
                .solve(
                    CaeSolverTest.problem(),
                    CaeSolverTest.settings(1, 2, 8, CaeSettings.Strategy.RANKED)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeSolver(cell -> BigDecimal.ZERO)
                .solve(
                    CaeSolverTest.problem(),
                    CaeSolverTest.settings(1, 2, 8, CaeSettings.Strategy.UNIFORM)));
    CaeCell root =
        CaeCellEvaluator.evaluate(
            CaeSolverTest.problem(),
            CaeSettings.standard(new BigDecimal("0.1")),
            CellPath.root(),
            new CaeControl(() -> false));
    assertThrows(
        IllegalStateException.class,
        () ->
            RefinementScores.score(
                root, CaeSolverTest.settings(1, 2, 8, CaeSettings.Strategy.RANKED)));
  }

  @Test
  void trialSplitLabelsKeepSignedRegressionsAndExactAttemptCounts() {
    CaeProblem problem =
        new CaeProblem(
            new io.github.gbalazsfalvi.fuzzy.maps.TentMap(),
            new io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership(
                java.util.List.of(
                    new io.github.gbalazsfalvi.fuzzy.membership.TentPeak(
                        io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint.of("1/2"),
                        io.github.gbalazsfalvi.fuzzy.arithmetic.Rational.ONE,
                        io.github.gbalazsfalvi.fuzzy.arithmetic.Rational.of(1, 2)),
                    new io.github.gbalazsfalvi.fuzzy.membership.TentPeak(
                        io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint.of("1/10"),
                        io.github.gbalazsfalvi.fuzzy.arithmetic.Rational.of(1, 20),
                        io.github.gbalazsfalvi.fuzzy.arithmetic.Rational.ONE))),
            0);
    CaeSettings settings = CaeSettings.standard(new BigDecimal("0.1"));
    CaeCell root =
        CaeCellEvaluator.evaluate(problem, settings, CellPath.root(), new CaeControl(() -> false));
    SplitProbe probe = SplitProbe.evaluate(problem, settings, CellPath.root());
    assertEquals(new BigDecimal("-0.125"), probe.gainAgainst(root));
    assertEquals(new CaeWork(2, 0, 0, 4, 2), probe.work());
    assertThrows(IllegalArgumentException.class, () -> probe.gainAgainst(probe.left()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SplitProbe(CellPath.root(), probe.right(), probe.right(), probe.work()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SplitProbe(CellPath.root(), probe.left(), probe.left(), probe.work()));
  }
}
