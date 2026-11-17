package io.github.gbalazsfalvi.fuzzy.cae;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.geometry.DecimalPoint;
import io.github.gbalazsfalvi.fuzzy.membership.MembershipSample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificateVerifierTest {
  private final CaeProblem problem = CaeSolverTest.problem();
  private final CaeSettings settings =
      CaeSolverTest.settings(100, 101, 16, CaeSettings.Strategy.MAXIMUM_BOUND);
  private final CertificateVerifier verifier = new CertificateVerifier();

  @Test
  void verifiesNonuniformCompletePrefixCovers() {
    for (List<String> paths :
        List.of(
            List.of(""),
            List.of("0", "10", "11"),
            List.of("00", "01", "1"),
            List.of("00", "010", "011", "10", "110", "111"))) {
      CaeSnapshot snapshot = snapshot(paths);
      CaeVerification result = verifier.verify(problem, settings, snapshot);
      assertTrue(result.valid(), result.issues().toString());
      assertEquals(paths.size(), result.work().cellAttempts());
    }
  }

  @Test
  void rejectsDuplicatesAncestryOverlapAndCoverageHoles() {
    for (List<String> paths :
        List.of(
            List.of("0", "0"),
            List.of("0", "00"),
            List.of("", "0"),
            List.of("0", "10"),
            List.of("00", "10"),
            List.of("0", "11"))) {
      CaeVerification result = verifier.verify(problem, settings, snapshot(paths));
      assertFalse(result.valid());
      assertTrue(result.issues().getFirst().contains("uncovered"));
      assertEquals(0, result.work().cellAttempts());
    }
  }

  @Test
  void rejectsDifferentProblemPrecisionAndRecipeContexts() {
    CaeSnapshot root = snapshot(List.of(""));
    for (CaeContext context :
        List.of(
            new CaeContext(
                "another-version", problem.description(), settings.arithmetic().precision()),
            new CaeContext(
                CaeContext.VERSION,
                problem.description() + "-changed",
                settings.arithmetic().precision()),
            new CaeContext(
                CaeContext.VERSION,
                problem.description(),
                settings.arithmetic().precision() + 1))) {
      assertFalse(
          verifier
              .verify(
                  problem,
                  settings,
                  new CaeSnapshot(context, root.refinement(), root.cells(), root.bounds()))
              .valid());
    }
  }

  @Test
  void rejectsInconsistentSnapshotCountsLimitsAndHorizon() {
    CaeSnapshot snapshot = snapshot(List.of("0", "10", "11"));
    assertFalse(
        verifier
            .verify(
                problem,
                settings,
                new CaeSnapshot(snapshot.context(), 1, snapshot.cells(), snapshot.bounds()))
            .valid());
    for (CaeSettings limited :
        List.of(
            CaeSolverTest.settings(1, 100, 16, CaeSettings.Strategy.MAXIMUM_BOUND),
            CaeSolverTest.settings(100, 2, 16, CaeSettings.Strategy.MAXIMUM_BOUND),
            CaeSolverTest.settings(100, 100, 1, CaeSettings.Strategy.MAXIMUM_BOUND))) {
      assertFalse(verifier.verify(problem, limited, snapshot).valid());
    }
    CaeCell cell = snapshot(List.of("")).cells().getFirst();
    CaeCell shortened =
        new CaeCell(
            cell.path(),
            cell.initial(),
            cell.membership(),
            cell.trajectory().subList(0, 1),
            cell.bounds().subList(0, 1));
    assertFalse(
        verifier
            .verify(
                problem,
                settings,
                new CaeSnapshot(snapshot.context(), 0, List.of(shortened), shortened.bounds()))
            .valid());
  }

  @Test
  void rejectsInventedBoundsGeometryRepresentativesAndMembership() {
    CaeSnapshot root = snapshot(List.of(""));
    CaeCell cell = root.cells().getFirst();
    List<BigDecimal> zero = cell.bounds().stream().map(value -> BigDecimal.ZERO).toList();
    List<CaeStep> radiusAltered = new ArrayList<>(cell.trajectory());
    CaeStep step = radiusAltered.getFirst();
    radiusAltered.set(
        0,
        new CaeStep(
            0,
            step.reachable(),
            step.representative(),
            BigDecimal.ZERO,
            step.directRadius(),
            step.propagatedRadius(),
            step.transitionLipschitz(),
            step.evaluationResidual()));
    List<CaeStep> representativeAltered = new ArrayList<>(cell.trajectory());
    representativeAltered.set(
        0,
        new CaeStep(
            0,
            step.reachable(),
            DecimalPoint.of("0.25"),
            step.radius(),
            step.directRadius(),
            step.propagatedRadius(),
            step.transitionLipschitz(),
            step.evaluationResidual()));
    List<CaeCell> altered =
        List.of(
            new CaeCell(cell.path(), cell.initial(), cell.membership(), cell.trajectory(), zero),
            new CaeCell(
                cell.path(),
                new CellPath("0").box(1),
                cell.membership(),
                cell.trajectory(),
                cell.bounds()),
            new CaeCell(
                cell.path(), cell.initial(), cell.membership(), radiusAltered, cell.bounds()),
            new CaeCell(
                cell.path(),
                cell.initial(),
                cell.membership(),
                representativeAltered,
                cell.bounds()),
            new CaeCell(
                cell.path(),
                cell.initial(),
                new MembershipSample(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                cell.trajectory(),
                zero));
    for (CaeCell forged : altered) {
      assertFalse(
          verifier
              .verify(
                  problem,
                  settings,
                  new CaeSnapshot(root.context(), 0, List.of(forged), forged.bounds()))
              .valid());
    }
    assertFalse(
        verifier
            .verify(problem, settings, new CaeSnapshot(root.context(), 0, root.cells(), zero))
            .valid());
  }

  private CaeSnapshot snapshot(List<String> paths) {
    List<CaeCell> cells =
        paths.stream()
            .map(
                path ->
                    CaeCellEvaluator.evaluate(
                        problem, settings, new CellPath(path), new CaeControl(() -> false)))
            .toList();
    List<BigDecimal> bounds = new ArrayList<>();
    for (int time = 0; time <= problem.horizon(); time++) {
      BigDecimal maximum = BigDecimal.ZERO;
      for (CaeCell cell : cells) {
        maximum = maximum.max(cell.bounds().get(time));
      }
      bounds.add(maximum);
    }
    return new CaeSnapshot(CaeContext.of(problem, settings), paths.size() - 1, cells, bounds);
  }
}
