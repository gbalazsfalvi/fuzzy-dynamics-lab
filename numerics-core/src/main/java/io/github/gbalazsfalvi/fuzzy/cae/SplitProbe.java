package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Complete trial child computations without modifying a solver partition.
 *
 * @param parentPath the initial cell being probed
 * @param left the complete left child
 * @param right the complete right child
 * @param work the actual numerical work spent on both children
 */
public record SplitProbe(CellPath parentPath, CaeCell left, CaeCell right, CaeWork work) {
  /**
   * Checks binary child identity and immutable evidence.
   *
   * @param parentPath the parent identity
   * @param left the first child
   * @param right the second child
   * @param work the actual work
   * @throws IllegalArgumentException if the children do not belong to this parent
   */
  public SplitProbe {
    Objects.requireNonNull(parentPath, "parentPath");
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    Objects.requireNonNull(work, "work");
    if (!left.path().equals(parentPath.child(0)) || !right.path().equals(parentPath.child(1))) {
      throw new IllegalArgumentException("A trial split needs both canonical children.");
    }
  }

  /**
   * Evaluates a complete candidate split using the canonical numerical recipe.
   *
   * @param problem the mathematical problem
   * @param settings the arithmetic policy; no selection score is consulted
   * @param parent the canonical parent path
   * @return both child certificates and their actual numerical cost
   */
  public static SplitProbe evaluate(CaeProblem problem, CaeSettings settings, CellPath parent) {
    CaeControl control = new CaeControl(() -> false);
    CaeCell left = CaeCellEvaluator.evaluate(problem, settings, parent.child(0), control);
    CaeCell right = CaeCellEvaluator.evaluate(problem, settings, parent.child(1), control);
    return new SplitProbe(parent, left, right, control.work());
  }

  /**
   * Measures the signed local horizon bound reduction, which can be negative.
   *
   * @param parent the verified original cell
   * @return parent bound minus the maximum child bound
   * @throws IllegalArgumentException if the supplied parent has another identity
   */
  public BigDecimal gainAgainst(CaeCell parent) {
    if (!parent.path().equals(parentPath)) {
      throw new IllegalArgumentException("The gain label must concern the probed parent.");
    }
    return parent.worstBound().subtract(left.worstBound().max(right.worstBound()));
  }
}
