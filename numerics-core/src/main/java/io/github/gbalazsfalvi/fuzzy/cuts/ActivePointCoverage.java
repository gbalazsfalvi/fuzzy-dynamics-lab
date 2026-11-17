package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Incremental exact directed coverage of a fixed interval union by activated target points.
 *
 * <p>The maximum occurs at a source endpoint or at a target gap midpoint inside the source. Only
 * the two neighboring gaps change on insertion, avoiding repeated full union reconstruction.
 */
final class ActivePointCoverage {
  private final IntervalUnion source;
  private final NavigableSet<Rational> points = new TreeSet<>();
  private final TreeMap<Rational, Integer> gapMaxima = new TreeMap<>();

  ActivePointCoverage(IntervalUnion source) {
    this.source = source;
  }

  void insert(Rational point) {
    if (points.contains(point)) {
      return;
    }
    Rational left = points.lower(point);
    Rational right = points.higher(point);
    updateGap(left, right, -1);
    points.add(point);
    updateGap(left, point, 1);
    updateGap(point, right, 1);
  }

  Rational maximumDistance() {
    Rational maximum = gapMaxima.isEmpty() ? Rational.ZERO : gapMaxima.lastKey();
    for (RationalInterval interval : source.intervals()) {
      maximum = maximum.max(distance(interval.lower())).max(distance(interval.upper()));
    }
    return maximum;
  }

  private Rational distance(Rational point) {
    Rational left = points.floor(point);
    Rational right = points.ceiling(point);
    if (left == null) {
      return right.subtract(point).abs();
    }
    if (right == null) {
      return point.subtract(left).abs();
    }
    return point.subtract(left).min(right.subtract(point));
  }

  private void updateGap(Rational left, Rational right, int delta) {
    if (left == null || right == null) {
      return;
    }
    Rational midpoint = left.add(right).divide(Rational.TWO);
    if (!source.contains(midpoint)) {
      return;
    }
    Rational score = right.subtract(left).divide(Rational.TWO);
    int count = gapMaxima.getOrDefault(score, 0) + delta;
    if (count == 0) {
      gapMaxima.remove(score);
    } else {
      gapMaxima.put(score, count);
    }
  }
}
