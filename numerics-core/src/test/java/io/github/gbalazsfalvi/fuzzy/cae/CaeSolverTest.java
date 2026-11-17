package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaeSolverTest {
  static CaeProblem problem() {
    return new CaeProblem(
        new AffineContraction(Rational.of(3, 4)),
        new TentPeak(ExactPoint.of("1/2"), Rational.of(3, 8), Rational.ONE),
        3);
  }

  static CaeSettings settings(
      int refinements, int cells, int depth, CaeSettings.Strategy strategy) {
    return new CaeSettings(
        new DecimalArithmetic(16), new BigDecimal("0.1"), refinements, cells, depth, 5, strategy);
  }

  @Test
  void reachesToleranceAcrossTheWholeHorizonAndReplaysBothSnapshots() {
    CaeProblem problem = problem();
    CaeSettings settings = CaeSettings.standard(new BigDecimal("0.1"));
    CaeResult result = new CaeSolver().solve(problem, settings);
    assertEquals(CaeResult.Status.CERTIFIED, result.status());
    assertEquals(CaeResult.StopReason.TOLERANCE_REACHED, result.reason());
    CaeSnapshot best = result.best().orElseThrow();
    assertTrue(
        best.bounds().stream().allMatch(bound -> bound.compareTo(settings.tolerance()) <= 0));
    assertEquals(best.cells().size() - 1, best.refinement());
    CertificateVerifier verifier = new CertificateVerifier();
    assertTrue(verifier.verify(problem, settings, best).valid());
    assertTrue(verifier.verify(problem, settings, result.last().orElseThrow()).valid());
    assertEquals(1 + 2L * result.history().size(), result.work().cellAttempts());
    assertEquals(result.work().cellAttempts() * 2L * problem.horizon(), result.work().mapImages());
    assertEquals(result.work().cellAttempts() * problem.horizon(), result.work().lipschitzBounds());
    assertEquals(result.work().cellAttempts() * 2, result.work().membershipRanges());
    assertEquals(result.work().cellAttempts(), result.work().membershipLipschitzBounds());
    assertThrows(UnsupportedOperationException.class, () -> best.cells().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.history().clear());
  }

  @Test
  void isExactlyDeterministicAndRetainsTheBetterSnapshotWhenRefinementWorsensError() {
    CaeProblem problem =
        new CaeProblem(
            new AffineContraction(Rational.of(3, 4)),
            new MultiPeakMembership(
                List.of(
                    new TentPeak(ExactPoint.of("1/2"), Rational.ONE, Rational.of(1, 2)),
                    new TentPeak(ExactPoint.of("1/10"), Rational.of(1, 20), Rational.ONE))),
            0);
    CaeSettings settings = settings(1, 8, 12, CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeResult first = new CaeSolver().solve(problem, settings);
    CaeResult second = new CaeSolver().solve(problem, settings);
    assertEquals(first, second);
    assertEquals(CaeResult.StopReason.MAX_REFINEMENTS, first.reason());
    assertEquals(0, first.best().orElseThrow().refinement());
    assertEquals(1, first.last().orElseThrow().refinement());
    assertEquals(1, first.best().orElseThrow().cells().size());
    assertEquals(2, first.last().orElseThrow().cells().size());
    assertEquals(0, first.best().orElseThrow().worstBound().compareTo(new BigDecimal("0.5")));
    assertEquals(0, first.last().orElseThrow().worstBound().compareTo(new BigDecimal("0.625")));
    BigDecimal best = BigDecimal.ONE;
    for (RefinementEvent event : first.history()) {
      assertTrue(event.bestBound().compareTo(best) <= 0);
      best = event.bestBound();
    }
  }

  @Test
  void maximumBoundRefinesTheRelevantRegionRatherThanAnOldZeroRegion() {
    CaeProblem problem =
        new CaeProblem(
            new TentMap(),
            new TentPeak(ExactPoint.of("1/10"), Rational.of(1, 20), Rational.ONE),
            2);
    CaeResult adaptive =
        new CaeSolver().solve(problem, settings(4, 20, 20, CaeSettings.Strategy.MAXIMUM_BOUND));
    CaeResult uniform =
        new CaeSolver().solve(problem, settings(4, 20, 20, CaeSettings.Strategy.UNIFORM));
    assertEquals("00", adaptive.history().get(2).parent().bits());
    assertEquals("1", uniform.history().get(2).parent().bits());
    assertTrue(
        uniform.history().stream()
            .allMatch(event -> event.selection() == RefinementEvent.Selection.UNIFORM));
  }

  @Test
  void periodicFairnessProcessesTheOldestEligibleLeaf() {
    CaeSettings settings = settings(24, 64, 8, CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeProblem problem =
        new CaeProblem(
            new TentMap(),
            new TentPeak(ExactPoint.of("1/10"), Rational.of(1, 20), Rational.ONE),
            4);
    CaeResult result = new CaeSolver().solve(problem, settings);
    LinkedHashSet<CellPath> active = new LinkedHashSet<>(List.of(CellPath.root()));
    int fair = 0;
    for (RefinementEvent event : result.history()) {
      if (event.refinement() % settings.fairnessPeriod() == 0) {
        assertEquals(RefinementEvent.Selection.FAIRNESS, event.selection());
        assertEquals(
            active.stream()
                .filter(path -> path.depth() < settings.maxDepth())
                .findFirst()
                .orElseThrow(),
            event.parent());
        fair++;
      }
      assertTrue(active.remove(event.parent()));
      active.add(event.parent().child(0));
      active.add(event.parent().child(1));
    }
    assertTrue(fair >= 3);
  }

  @Test
  void handlesEachDeterministicResourceLimit() {
    assertEquals(
        CaeResult.StopReason.MAX_REFINEMENTS,
        new CaeSolver()
            .solve(problem(), settings(0, 10, 10, CaeSettings.Strategy.MAXIMUM_BOUND))
            .reason());
    assertEquals(
        CaeResult.StopReason.MAX_CELLS,
        new CaeSolver()
            .solve(problem(), settings(10, 1, 10, CaeSettings.Strategy.MAXIMUM_BOUND))
            .reason());
    for (CaeSettings.Strategy strategy :
        java.util.Arrays.stream(CaeSettings.Strategy.values())
            .filter(value -> value != CaeSettings.Strategy.RANKED)
            .toList()) {
      CaeResult depth = new CaeSolver().solve(problem(), settings(30, 100, 1, strategy));
      assertEquals(CaeResult.StopReason.MAX_DEPTH, depth.reason());
      assertTrue(
          depth.last().orElseThrow().cells().stream().allMatch(cell -> cell.path().depth() == 1));
    }
  }

  @Test
  void keepsZeroAndLowGradeProfilesWithinTheBaselineCapWithoutRefinement() {
    for (Rational height : List.of(Rational.ZERO, Rational.of(1, 100))) {
      CaeProblem problem =
          new CaeProblem(
              new TentMap(), new TentPeak(ExactPoint.of("1/5"), Rational.of(1, 20), height), 6);
      CaeSettings settings = CaeSettings.standard(new BigDecimal("0.02"));
      CaeResult result = new CaeSolver().solve(problem, settings);
      assertEquals(CaeResult.Status.CERTIFIED, result.status());
      assertTrue(result.history().isEmpty());
      assertEquals(
          0,
          result
              .best()
              .orElseThrow()
              .worstBound()
              .compareTo(height.toDecimal(new java.math.MathContext(10))));
      assertTrue(
          new CertificateVerifier().verify(problem, settings, result.best().orElseThrow()).valid());
    }
  }

  @Test
  void cancellationBeforeInitializationReturnsNoFabricatedCertificate() {
    CaeResult result =
        new CaeSolver().solve(problem(), CaeSettings.standard(new BigDecimal("0.1")), () -> true);
    assertEquals(CaeResult.Status.CANCELLED, result.status());
    assertTrue(result.best().isEmpty());
    assertTrue(result.last().isEmpty());
    assertEquals(0, result.work().cellAttempts());
  }

  @Test
  void cancellationAndArithmeticFailureDiscardPartialChildrenAtomically() {
    for (boolean cancel : List.of(false, true)) {
      AtomicInteger calls = new AtomicInteger();
      AtomicBoolean cancelled = new AtomicBoolean();
      CaeSolver solver =
          new CaeSolver(
              (problem, settings, path, control) -> {
                int call = calls.incrementAndGet();
                if (call == 3 && !cancel) {
                  throw new ArithmeticException("Injected failed second child.");
                }
                CaeCell cell = CaeCellEvaluator.evaluate(problem, settings, path, control);
                if (call == 2 && cancel) {
                  cancelled.set(true);
                }
                return cell;
              });
      CaeSettings settings = settings(10, 30, 10, CaeSettings.Strategy.MAXIMUM_BOUND);
      CaeResult result = solver.solve(problem(), settings, cancelled::get);
      assertEquals(
          cancel ? CaeResult.Status.CANCELLED : CaeResult.Status.NUMERICAL_FAILURE,
          result.status());
      assertEquals(0, result.last().orElseThrow().refinement());
      assertEquals(1, result.last().orElseThrow().cells().size());
      assertTrue(result.history().isEmpty());
      assertTrue(result.work().cellAttempts() >= 2);
      assertTrue(
          new CertificateVerifier()
              .verify(problem(), settings, result.best().orElseThrow())
              .valid());
    }
    CaeSolver failure =
        new CaeSolver(
            (problem, settings, path, control) -> {
              throw new ArithmeticException("Injected initialization failure.");
            });
    CaeResult failed = failure.solve(problem(), CaeSettings.standard(new BigDecimal("0.1")));
    assertEquals(CaeResult.Status.NUMERICAL_FAILURE, failed.status());
    assertTrue(failed.best().isEmpty());
  }

  @Test
  void lateInterruptionRetainsCompletedRefinementsAndCountsDiscardedWork() {
    for (boolean cancel : List.of(false, true)) {
      AtomicInteger calls = new AtomicInteger();
      AtomicBoolean cancelled = new AtomicBoolean();
      CaeSolver solver =
          new CaeSolver(
              (problem, settings, path, control) -> {
                int call = calls.incrementAndGet();
                CaeCell cell = CaeCellEvaluator.evaluate(problem, settings, path, control);
                if (call == 6 && cancel) {
                  cancelled.set(true);
                }
                if (call == 7 && !cancel) {
                  throw new ArithmeticException(
                      "Injected failure before committing the third split.");
                }
                return cell;
              });
      CaeSettings settings = settings(10, 30, 10, CaeSettings.Strategy.MAXIMUM_BOUND);
      CaeResult result = solver.solve(problem(), settings, cancelled::get);
      assertEquals(
          cancel ? CaeResult.Status.CANCELLED : CaeResult.Status.NUMERICAL_FAILURE,
          result.status());
      assertEquals(2, result.history().size());
      assertEquals(2, result.last().orElseThrow().refinement());
      assertEquals(3, result.last().orElseThrow().cells().size());
      assertEquals(cancel ? 6 : 7, result.work().cellAttempts());
      assertTrue(
          new CertificateVerifier()
              .verify(problem(), settings, result.best().orElseThrow())
              .valid());
      assertTrue(
          new CertificateVerifier()
              .verify(problem(), settings, result.last().orElseThrow())
              .valid());
    }
  }

  @Test
  void certifiesASeparatedTwoDimensionalRegionAndConstantSpatialMap() {
    List<CertifiedMap> maps =
        List.of(new CoupledTentMap(Rational.of(1, 4)), new AffineContraction(Rational.ZERO));
    for (CertifiedMap map : maps) {
      TentPeak profile =
          new TentPeak(
              map.dimension() == 1 ? ExactPoint.of("1/2") : ExactPoint.of("1/4", "3/4"),
              Rational.of(1, 4),
              Rational.ONE);
      CaeProblem problem = new CaeProblem(map, profile, 2);
      CaeSettings settings =
          new CaeSettings(
              new DecimalArithmetic(16),
              new BigDecimal("0.25"),
              300,
              301,
              18,
              5,
              CaeSettings.Strategy.MAXIMUM_BOUND);
      CaeResult result = new CaeSolver().solve(problem, settings);
      assertEquals(CaeResult.Status.CERTIFIED, result.status());
      assertTrue(
          new CertificateVerifier().verify(problem, settings, result.best().orElseThrow()).valid());
    }
  }
}
