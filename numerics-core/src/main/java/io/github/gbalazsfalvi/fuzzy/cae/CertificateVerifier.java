package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconstructs full coverage and recomputes every numerical quantity from the original problem.
 *
 * <p>This is a strict verifier for the canonical versioned recipe, not a checker of arbitrary
 * looser certificates. It shares the trusted interval primitives and cell recipe with generation;
 * independent analytic and exact tests are still needed to detect implementation errors.
 */
public final class CertificateVerifier {
  /** Creates the canonical recomputation verifier. */
  public CertificateVerifier() {}

  /**
   * Checks a complete snapshot without trusting its reported geometry or bounds.
   *
   * @param problem the original mathematical problem
   * @param settings the original numerical and resource settings
   * @param snapshot the possibly modified certificate data
   * @return the rejection details and actual verification work
   * @throws ArithmeticException if verified recomputation cannot be completed
   */
  public CaeVerification verify(CaeProblem problem, CaeSettings settings, CaeSnapshot snapshot) {
    CaeControl control = new CaeControl(() -> false);
    if (!snapshot.context().equals(CaeContext.of(problem, settings))) {
      return rejected(
          "The certificate context does not match the problem, precision, or recipe.", control);
    }
    if (snapshot.refinement() != snapshot.cells().size() - 1
        || snapshot.refinement() > settings.maxRefinements()
        || snapshot.cells().size() > settings.maxCells()
        || snapshot.bounds().size() != problem.horizon() + 1
        || snapshot.cells().stream().anyMatch(cell -> cell.path().depth() > settings.maxDepth())) {
      return rejected("The snapshot shape or resource limits do not match the run.", control);
    }
    if (!coversRoot(snapshot.cells().stream().map(CaeCell::path).toList())) {
      return rejected(
          "Cell paths are duplicated, overlap by ancestry, or leave the unit domain uncovered.",
          control);
    }
    List<BigDecimal> aggregate = new ArrayList<>();
    for (int time = 0; time <= problem.horizon(); time++) {
      aggregate.add(BigDecimal.ZERO);
    }
    for (CaeCell claimed : snapshot.cells()) {
      CaeCell expected = CaeCellEvaluator.evaluate(problem, settings, claimed.path(), control);
      if (!expected.equals(claimed)) {
        return rejected(
            "Cell "
                + claimed.path()
                + " differs from its recomputed geometry, membership, trajectory, or bounds.",
            control);
      }
      for (int time = 0; time < aggregate.size(); time++) {
        aggregate.set(time, aggregate.get(time).max(expected.bounds().get(time)));
      }
    }
    if (!aggregate.equals(snapshot.bounds())) {
      return rejected("The reported horizon bounds do not equal the recomputed envelope.", control);
    }
    return new CaeVerification(List.of(), control.work());
  }

  private static CaeVerification rejected(String issue, CaeControl control) {
    return new CaeVerification(List.of(issue), control.work());
  }

  private static boolean coversRoot(List<CellPath> paths) {
    Set<String> unique = new HashSet<>();
    for (CellPath path : paths) {
      if (!unique.add(path.bits())) {
        return false;
      }
    }
    List<String> ordered = unique.stream().sorted().toList();
    for (int index = 1; index < ordered.size(); index++) {
      if (ordered.get(index).startsWith(ordered.get(index - 1))) {
        return false;
      }
    }
    // Collapsing adjacent sibling subtrees is an exact prefix-cover check, with no volume rounding.
    List<String> stack = new ArrayList<>();
    for (String path : ordered) {
      stack.add(path);
      while (stack.size() >= 2) {
        String right = stack.getLast();
        String left = stack.get(stack.size() - 2);
        if (!right.endsWith("1")) {
          break;
        }
        String parent = right.substring(0, right.length() - 1);
        if (!left.equals(parent + "0")) {
          break;
        }
        stack.removeLast();
        stack.removeLast();
        stack.add(parent);
      }
    }
    return stack.size() == 1 && stack.getFirst().isEmpty();
  }
}
