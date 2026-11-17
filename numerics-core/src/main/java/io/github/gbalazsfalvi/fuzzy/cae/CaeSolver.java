package io.github.gbalazsfalvi.fuzzy.cae;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Deterministic adaptive CAE with verified bounds, FIFO fairness, and atomic child replacement.
 *
 * <p>No wall clock budget or random choice influences a completed run. Cancellation is cooperative.
 * A failed or cancelled child computation leaves the last complete partition untouched.
 */
public final class CaeSolver {
  private final CellComputer computer;
  private final CellRanker ranker;

  /** Creates the canonical interval and Lipschitz solver. */
  public CaeSolver() {
    this(CaeCellEvaluator::evaluate, null);
  }

  /**
   * Creates the canonical solver with an external selection score for the RANKED strategy.
   *
   * @param ranker the deterministic cell ranker, independent of all certificate calculations
   */
  public CaeSolver(CellRanker ranker) {
    this(CaeCellEvaluator::evaluate, Objects.requireNonNull(ranker, "ranker"));
  }

  CaeSolver(CellComputer computer) {
    this(computer, null);
  }

  private CaeSolver(CellComputer computer, CellRanker ranker) {
    this.computer = Objects.requireNonNull(computer, "computer");
    this.ranker = ranker;
  }

  /**
   * Solves a supported problem without external cancellation.
   *
   * @param problem the mathematical problem
   * @param settings the numerical and resource settings
   * @return the complete local outcome
   */
  public CaeResult solve(CaeProblem problem, CaeSettings settings) {
    return solve(problem, settings, () -> false);
  }

  /**
   * Solves with cancellation checked between verified numerical operations.
   *
   * @param problem the mathematical problem
   * @param settings the numerical and resource settings
   * @param cancelled a callback returning true when interruption is requested
   * @return the outcome and any complete retained approximations
   */
  public CaeResult solve(CaeProblem problem, CaeSettings settings, BooleanSupplier cancelled) {
    Objects.requireNonNull(problem, "problem");
    Objects.requireNonNull(settings, "settings");
    if ((settings.strategy() == CaeSettings.Strategy.RANKED) != (ranker != null)) {
      throw new IllegalArgumentException(
          "The RANKED strategy requires exactly one explicit ranker.");
    }
    CaeControl control = new CaeControl(cancelled);
    List<RefinementEvent> history = new ArrayList<>();
    ActivePartition partition = null;
    CaeSnapshot best = null;
    try {
      CaeCell root = computer.compute(problem, settings, CellPath.root(), control);
      partition = new ActivePartition(CaeContext.of(problem, settings), root, settings, ranker);
      best = partition.snapshot(0);
      while (true) {
        if (best.worstBound().compareTo(settings.tolerance()) <= 0) {
          return finish(
              CaeResult.StopReason.TOLERANCE_REACHED,
              "The complete horizon meets tolerance.",
              best,
              partition,
              history,
              control);
        }
        control.check();
        if (history.size() >= settings.maxRefinements()) {
          return finish(
              CaeResult.StopReason.MAX_REFINEMENTS,
              "The refinement budget was reached.",
              best,
              partition,
              history,
              control);
        }
        if (partition.size() >= settings.maxCells()) {
          return finish(
              CaeResult.StopReason.MAX_CELLS,
              "Another split would exceed the leaf budget.",
              best,
              partition,
              history,
              control);
        }
        Optional<ActivePartition.Selected> selected = partition.select(history.size() + 1);
        if (selected.isEmpty()) {
          return finish(
              CaeResult.StopReason.MAX_DEPTH,
              "Every active leaf is at the depth limit.",
              best,
              partition,
              history,
              control);
        }
        ActivePartition.Selected choice = selected.orElseThrow();
        CaeCell parent = choice.cell();
        // Complete both computations before mutating any partition or envelope index.
        CaeCell left = computer.compute(problem, settings, parent.path().child(0), control);
        CaeCell right = computer.compute(problem, settings, parent.path().child(1), control);
        partition.replace(parent, left, right);
        int refinement = history.size() + 1;
        var currentBound = CaeMath.maximum(partition.bounds());
        if (currentBound.compareTo(best.worstBound()) < 0) {
          best = partition.snapshot(refinement);
        }
        history.add(
            new RefinementEvent(
                refinement,
                parent.path(),
                choice.reason(),
                currentBound,
                best.worstBound(),
                partition.size()));
      }
    } catch (CaeControl.Cancelled exception) {
      return finish(
          CaeResult.StopReason.CANCEL_REQUESTED,
          exception.getMessage(),
          best,
          partition,
          history,
          control);
    } catch (ArithmeticException exception) {
      return finish(
          CaeResult.StopReason.ARITHMETIC_FAILURE,
          "Verified arithmetic failed: " + exception,
          best,
          partition,
          history,
          control);
    }
  }

  private static CaeResult finish(
      CaeResult.StopReason reason,
      String detail,
      CaeSnapshot best,
      ActivePartition partition,
      List<RefinementEvent> history,
      CaeControl control) {
    Optional<CaeSnapshot> last =
        partition == null ? Optional.empty() : Optional.of(partition.snapshot(history.size()));
    return new CaeResult(
        reason.status(), reason, detail, Optional.ofNullable(best), last, history, control.work());
  }

  /**
   * Package-local numerical seam permits failure injection without widening the supported map API.
   */
  @FunctionalInterface
  interface CellComputer {
    CaeCell compute(CaeProblem problem, CaeSettings settings, CellPath path, CaeControl control);
  }
}
