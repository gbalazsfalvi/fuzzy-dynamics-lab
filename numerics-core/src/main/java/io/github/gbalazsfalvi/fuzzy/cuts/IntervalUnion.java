package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A canonical finite union of exact closed unit intervals; empty unions and isolated points
 * survive.
 *
 * @param intervals the sorted disjoint components after merging overlaps and touching boundaries
 */
public record IntervalUnion(List<RationalInterval> intervals) {
  /**
   * Copies and canonicalizes all components without filling gaps.
   *
   * @param intervals the possibly empty unordered input components
   */
  public IntervalUnion {
    List<RationalInterval> sorted =
        intervals.stream()
            .sorted(
                Comparator.comparing(RationalInterval::lower)
                    .thenComparing(RationalInterval::upper))
            .toList();
    List<RationalInterval> merged = new ArrayList<>();
    for (RationalInterval next : sorted) {
      if (merged.isEmpty() || merged.getLast().upper().compareTo(next.lower()) < 0) {
        merged.add(next);
      } else {
        RationalInterval previous = merged.removeLast();
        merged.add(new RationalInterval(previous.lower(), previous.upper().max(next.upper())));
      }
    }
    intervals = List.copyOf(merged);
  }

  /**
   * Tests membership in any complete component.
   *
   * @param point the exact coordinate
   * @return whether the point belongs to this union
   */
  public boolean contains(Rational point) {
    return intervals.stream().anyMatch(interval -> interval.contains(point));
  }

  /**
   * Computes the nearest exact distance without replacing the union with a convex hull.
   *
   * @param point the coordinate
   * @return the distance to a nonempty union
   * @throws IllegalStateException if the union is empty
   */
  public Rational distance(Rational point) {
    return intervals.stream()
        .map(interval -> interval.distance(point))
        .min(Rational::compareTo)
        .orElseThrow(() -> new IllegalStateException("Distance to an empty cut is undefined."));
  }

  /**
   * Computes the Hausdorff distance between nonempty compact interval unions.
   *
   * @param other the nonempty second cut
   * @return the exact symmetric distance
   * @throws IllegalStateException if either cut is empty
   */
  public Rational hausdorff(IntervalUnion other) {
    if (intervals.isEmpty() || other.intervals.isEmpty()) {
      throw new IllegalStateException(
          "Ordinary cut Hausdorff distance requires two nonempty cuts.");
    }
    return directedTo(other).max(other.directedTo(this));
  }

  /**
   * Computes the directed spatial Hausdorff distance to a nonempty union.
   *
   * @param other the nonempty target
   * @return zero for an empty source, otherwise its exact directed distance
   * @throws IllegalStateException if the target is empty
   */
  public Rational directedTo(IntervalUnion other) {
    if (other.intervals.isEmpty()) {
      throw new IllegalStateException("A directed cut distance needs a nonempty target.");
    }
    Rational worst = Rational.ZERO;
    for (RationalInterval source : intervals) {
      worst = worst.max(other.distance(source.lower())).max(other.distance(source.upper()));
      // Distance to a union is affine outside its hull and a tent over each interior gap.
      // Its only additional possible maxima occur at those gap midpoints.
      for (int index = 1; index < other.intervals.size(); index++) {
        Rational midpoint =
            other
                .intervals
                .get(index - 1)
                .upper()
                .add(other.intervals.get(index).lower())
                .divide(Rational.TWO);
        if (source.contains(midpoint)) {
          worst = worst.max(other.distance(midpoint));
        }
      }
    }
    return worst;
  }
}
