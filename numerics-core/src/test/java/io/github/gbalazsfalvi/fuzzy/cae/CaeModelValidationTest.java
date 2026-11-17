package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CaeModelValidationTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal ONE = BigDecimal.ONE;
  private static final BigDecimal NEGATIVE = ONE.negate();

  @Test
  void rejectsUnsupportedProblemAndIdentityDefinitions() {
    TentPeak one = new TentPeak(ExactPoint.of("1/2"), Rational.ONE, Rational.ONE);
    TentPeak two = new TentPeak(ExactPoint.of("1/2", "1/2"), Rational.ONE, Rational.ONE);
    assertThrows(IllegalArgumentException.class, () -> new CaeProblem(new TentMap(), two, 0));
    assertThrows(IllegalArgumentException.class, () -> new CaeProblem(new TentMap(), one, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeProblem(new TentMap(), one, Integer.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> new CaeContext(" ", "problem", 34));
    assertThrows(IllegalArgumentException.class, () -> new CaeContext("v1", " ", 34));
    assertThrows(IllegalArgumentException.class, () -> new CaeContext("v1", "problem", 0));
    assertThrows(IllegalArgumentException.class, () -> new CellPath("012"));
    assertThrows(IllegalArgumentException.class, () -> CellPath.root().child(-1));
    assertThrows(IllegalArgumentException.class, () -> CellPath.root().child(2));
    assertEquals("root", CellPath.root().toString());
    assertEquals("01", CellPath.root().child(0).child(1).toString());
  }

  @Test
  void rejectsTrivialToleranceAndInvalidBudgetsIndividually() {
    DecimalArithmetic arithmetic = new DecimalArithmetic(34);
    CaeSettings.Strategy strategy = CaeSettings.Strategy.MAXIMUM_BOUND;
    for (BigDecimal tolerance : List.of(ZERO, NEGATIVE, ONE, new BigDecimal("1.1"))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new CaeSettings(arithmetic, tolerance, 1, 2, 3, 5, strategy));
    }
    BigDecimal tolerance = new BigDecimal("0.01");
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSettings(arithmetic, tolerance, -1, 2, 3, 5, strategy));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSettings(arithmetic, tolerance, 1, 0, 3, 5, strategy));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSettings(arithmetic, tolerance, 1, 2, -1, 5, strategy));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSettings(arithmetic, tolerance, 1, 2, 3, 0, strategy));
  }

  @Test
  void rejectsNegativeWorkAndMalformedRefinementEvents() {
    for (int position = 0; position < 5; position++) {
      long[] counts = {0, 0, 0, 0, 0};
      counts[position] = -1;
      assertThrows(
          IllegalArgumentException.class,
          () -> new CaeWork(counts[0], counts[1], counts[2], counts[3], counts[4]));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefinementEvent(
                0, CellPath.root(), RefinementEvent.Selection.FAIRNESS, ONE, ZERO, 2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefinementEvent(
                1, CellPath.root(), RefinementEvent.Selection.FAIRNESS, ONE, ZERO, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefinementEvent(
                1,
                CellPath.root(),
                RefinementEvent.Selection.FAIRNESS,
                new BigDecimal("1.1"),
                ZERO,
                2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefinementEvent(
                1, CellPath.root(), RefinementEvent.Selection.FAIRNESS, ZERO, ONE, 2));
  }

  @Test
  void rejectsInvalidTrajectoryAndSnapshotShapesBeforeReplay() {
    CaeSettings settings = CaeSolverTest.settings(2, 3, 5, CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeCell root =
        CaeCellEvaluator.evaluate(
            CaeSolverTest.problem(), settings, CellPath.root(), new CaeControl(() -> false));
    CaeContext context = CaeContext.of(CaeSolverTest.problem(), settings);
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeStep(-1, Box.unit(1), DecimalPoint.of("0.5"), ONE, ONE, ONE, ZERO, ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeStep(0, Box.unit(2), DecimalPoint.of("0.5"), ONE, ONE, ONE, ZERO, ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeStep(0, Box.unit(1), DecimalPoint.of("0.5"), NEGATIVE, ONE, ONE, ZERO, ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSnapshot(context, -1, List.of(root), root.bounds()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSnapshot(context, 0, List.of(), root.bounds()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSnapshot(context, 0, List.of(root), List.of(ONE)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSnapshot(context, 0, List.of(root), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeSnapshot(context, 0, List.of(root), List.of(new BigDecimal("1.1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeCell(
                root.path(), root.initial(), root.membership(), root.trajectory(), List.of(ONE)));
    List<CaeStep> incorrectTime = new ArrayList<>(root.trajectory());
    incorrectTime.set(0, root.trajectory().get(1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeCell(
                root.path(), root.initial(), root.membership(), incorrectTime, root.bounds()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeCell(
                root.path(), Box.unit(2), root.membership(), root.trajectory(), root.bounds()));
    CaeSnapshot snapshot = new CaeSnapshot(context, 0, List.of(root), root.bounds());
    assertThrows(IllegalArgumentException.class, () -> snapshot.approximation(-1));
    assertThrows(
        IllegalArgumentException.class, () -> snapshot.approximation(root.bounds().size()));
  }

  @Test
  void rejectsInconsistentOutcomeStateAndIncumbentOrdering() {
    CaeResult result =
        new CaeSolver()
            .solve(
                CaeSolverTest.problem(),
                CaeSolverTest.settings(2, 3, 5, CaeSettings.Strategy.MAXIMUM_BOUND));
    CaeSnapshot best = result.best().orElseThrow();
    CaeSnapshot last = result.last().orElseThrow();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                CaeResult.Status.CERTIFIED,
                CaeResult.StopReason.MAX_CELLS,
                "",
                result.best(),
                result.last(),
                result.history(),
                result.work()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                result.status(),
                result.reason(),
                "",
                result.best(),
                Optional.empty(),
                List.of(),
                result.work()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                CaeResult.Status.CERTIFIED,
                CaeResult.StopReason.TOLERANCE_REACHED,
                "",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                result.work()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                result.status(),
                result.reason(),
                "",
                result.best(),
                result.last(),
                List.of(),
                result.work()));
    CaeSnapshot later =
        new CaeSnapshot(best.context(), last.refinement() + 1, best.cells(), best.bounds());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                result.status(),
                result.reason(),
                "",
                Optional.of(later),
                result.last(),
                result.history(),
                result.work()));
    CaeSnapshot worse =
        new CaeSnapshot(
            best.context(), 0, best.cells(), best.bounds().stream().map(ignored -> ONE).toList());
    CaeSnapshot betterLast =
        new CaeSnapshot(
            last.context(),
            last.refinement(),
            last.cells(),
            last.bounds().stream().map(ignored -> ZERO).toList());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                result.status(),
                result.reason(),
                "",
                Optional.of(worse),
                Optional.of(betterLast),
                result.history(),
                result.work()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeResult(
                CaeResult.Status.CANCELLED,
                CaeResult.StopReason.CANCEL_REQUESTED,
                "",
                Optional.empty(),
                Optional.empty(),
                result.history(),
                result.work()));
  }

  @Test
  void rejectsInvalidInternalReplacementsBeforeMutatingThePartition() {
    CaeProblem problem = CaeSolverTest.problem();
    CaeSettings settings = CaeSolverTest.settings(2, 3, 5, CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeControl control = new CaeControl(() -> false);
    CaeCell root = CaeCellEvaluator.evaluate(problem, settings, CellPath.root(), control);
    CaeCell left = CaeCellEvaluator.evaluate(problem, settings, CellPath.root().child(0), control);
    CaeCell right = CaeCellEvaluator.evaluate(problem, settings, CellPath.root().child(1), control);
    ActivePartition partition =
        new ActivePartition(CaeContext.of(problem, settings), root, settings);
    CaeSnapshot before = partition.snapshot(0);
    assertThrows(IllegalStateException.class, () -> partition.replace(left, left, right));
    assertThrows(IllegalStateException.class, () -> partition.replace(root, right, right));
    assertThrows(IllegalStateException.class, () -> partition.replace(root, left, left));
    assertEquals(before, partition.snapshot(0));
  }
}
