package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;

/**
 * A deterministic score used only to rank complete cells, never to supply a numerical bound.
 *
 * <p>The implementation must return a nonnull score consistently for identical cell data. A signed
 * score is allowed. Higher scores are selected first, with creation age breaking ties and the
 * configured periodic FIFO rule preserving fairness.
 */
@FunctionalInterface
public interface CellRanker {
  /**
   * Ranks a complete immutable cell.
   *
   * @param cell the verified cell and its complete trajectory
   * @return its finite decimal selection score
   * @throws ArithmeticException if a score cannot be evaluated; the solver retains its last cover
   */
  BigDecimal score(CaeCell cell);
}
