package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A complete immutable partition and its horizon bounds.
 *
 * @param context the mathematical and arithmetic identity
 * @param refinement the number of completed splits at this snapshot
 * @param cells the complete active partition in creation order
 * @param bounds the per time maxima of all cell contributions
 */
public record CaeSnapshot(
    CaeContext context, int refinement, List<CaeCell> cells, List<BigDecimal> bounds) {
  /**
   * Copies snapshot data without assuming that supplied coverage or bounds are true.
   *
   * @param context the certificate identity
   * @param refinement the nonnegative refinement index
   * @param cells the nonempty active cell list
   * @param bounds the nonempty time bounds
   * @throws IllegalArgumentException if basic snapshot shape checks fail
   */
  public CaeSnapshot {
    Objects.requireNonNull(context, "context");
    cells = List.copyOf(cells);
    bounds = CaeMath.bounds(bounds);
    int timeCount = bounds.size();
    if (refinement < 0
        || cells.isEmpty()
        || cells.stream().anyMatch(cell -> cell.bounds().size() != timeCount)) {
      throw new IllegalArgumentException(
          "A snapshot needs a complete, consistently shaped partition.");
    }
  }

  /**
   * Returns the bound covering every requested time.
   *
   * @return the largest time bound
   */
  public BigDecimal worstBound() {
    return CaeMath.maximum(bounds);
  }

  /**
   * Materializes the exact finite representation of the stored decimal approximation.
   *
   * @param time the requested time
   * @return a finite fuzzy state with collisions combined by maximum
   * @throws IllegalArgumentException if the time is outside the snapshot horizon
   */
  public FiniteFuzzySet approximation(int time) {
    if (time < 0 || time >= bounds.size()) {
      throw new IllegalArgumentException("The requested time is outside the snapshot horizon.");
    }
    return new FiniteFuzzySet(
        cells.getFirst().initial().dimension(),
        cells.stream()
            .map(
                cell ->
                    new FuzzyAtom(
                        cell.trajectory().get(time).representative().toExact(),
                        Rational.fromDecimal(cell.membership().grade())))
            .toList());
  }
}
