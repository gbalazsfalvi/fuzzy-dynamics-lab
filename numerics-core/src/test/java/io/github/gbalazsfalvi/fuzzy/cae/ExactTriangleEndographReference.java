package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Independent continuous one dimensional oracle, using exact piecewise linear optimization.
 *
 * <p>On each triangle segment, every distance is a finite min/max of affine functions. Its extrema
 * occur at segment endpoints or crossings of those affine functions. Enumerating all crossings
 * therefore gives the exact continuous answer; it is not a grid or the CAE error formula.
 */
final class ExactTriangleEndographReference {
  private ExactTriangleEndographReference() {}

  static Rational distance(
      Rational center, Rational width, Rational height, FiniteFuzzySet finite) {
    List<Segment> segments = segments(center, width, height);
    Rational forward = Rational.ZERO;
    for (Segment segment : segments) {
      List<Line> lines =
          new ArrayList<>(List.of(segment.membership(), new Line(Rational.ZERO, Rational.ZERO)));
      for (FuzzyAtom atom : finite.atoms()) {
        Rational location = atom.location().coordinate(0);
        lines.add(new Line(Rational.ONE, location.negate()));
        lines.add(new Line(Rational.ONE.negate(), location));
        lines.add(
            new Line(
                segment.membership().slope(),
                segment.membership().intercept().subtract(atom.grade())));
      }
      for (Rational x : criticalPoints(segment, lines)) {
        Rational membership = segment.membership().at(x);
        Rational nearest = membership; // Complete common zero baseline.
        for (FuzzyAtom atom : finite.atoms()) {
          Rational spatial = x.subtract(atom.location().coordinate(0)).abs();
          Rational vertical = membership.subtract(atom.grade()).max(Rational.ZERO);
          nearest = nearest.min(spatial.max(vertical));
        }
        forward = forward.max(nearest);
      }
    }

    Rational reverse = Rational.ZERO;
    for (FuzzyAtom atom : finite.atoms()) {
      Rational location = atom.location().coordinate(0);
      Rational nearest = atom.grade();
      for (Segment segment : segments) {
        List<Line> lines =
            List.of(
                new Line(Rational.ONE, location.negate()),
                new Line(Rational.ONE.negate(), location),
                new Line(
                    segment.membership().slope().negate(),
                    atom.grade().subtract(segment.membership().intercept())),
                new Line(Rational.ZERO, Rational.ZERO));
        for (Rational x : criticalPoints(segment, lines)) {
          Rational spatial = x.subtract(location).abs();
          Rational vertical = atom.grade().subtract(segment.membership().at(x)).max(Rational.ZERO);
          nearest = nearest.min(spatial.max(vertical));
        }
      }
      reverse = reverse.max(nearest);
    }
    return forward.max(reverse);
  }

  private static List<Segment> segments(Rational center, Rational width, Rational height) {
    TreeSet<Rational> knots = new TreeSet<>(List.of(Rational.ZERO, Rational.ONE));
    for (Rational x : List.of(center.subtract(width), center, center.add(width))) {
      if (x.compareTo(Rational.ZERO) > 0 && x.compareTo(Rational.ONE) < 0) {
        knots.add(x);
      }
    }
    List<Rational> ordered = new ArrayList<>(knots);
    List<Segment> result = new ArrayList<>();
    for (int index = 1; index < ordered.size(); index++) {
      Rational lo = ordered.get(index - 1);
      Rational hi = ordered.get(index);
      Rational left = membership(lo, center, width, height);
      Rational right = membership(hi, center, width, height);
      Rational slope = right.subtract(left).divide(hi.subtract(lo));
      result.add(new Segment(lo, hi, new Line(slope, left.subtract(slope.multiply(lo)))));
    }
    return result;
  }

  private static Rational membership(Rational x, Rational center, Rational width, Rational height) {
    return Rational.ONE
        .subtract(x.subtract(center).abs().divide(width))
        .max(Rational.ZERO)
        .multiply(height);
  }

  private static List<Rational> criticalPoints(Segment segment, List<Line> lines) {
    TreeSet<Rational> points = new TreeSet<>(List.of(segment.lo(), segment.hi()));
    for (int first = 0; first < lines.size(); first++) {
      for (int second = first + 1; second < lines.size(); second++) {
        Line a = lines.get(first);
        Line b = lines.get(second);
        Rational denominator = a.slope().subtract(b.slope());
        if (!denominator.equals(Rational.ZERO)) {
          Rational crossing = b.intercept().subtract(a.intercept()).divide(denominator);
          if (crossing.compareTo(segment.lo()) >= 0 && crossing.compareTo(segment.hi()) <= 0) {
            points.add(crossing);
          }
        }
      }
    }
    return new ArrayList<>(points);
  }

  private record Line(Rational slope, Rational intercept) {
    Rational at(Rational x) {
      return slope.multiply(x).add(intercept);
    }
  }

  private record Segment(Rational lo, Rational hi, Line membership) {}
}
