package io.github.gbalazsfalvi.fuzzy.fuzzy;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FiniteFuzzySetTest {
  @Test
  void combinesCollisionsByMaximumAndKeepsCanonicalImmutableAtoms() {
    FiniteFuzzySet source = state(atom("1/4", "1/5"), atom("3/4", "4/5"), atom("0", "0"));
    FiniteFuzzySet image = source.extend(new TentMap());
    assertEquals(List.of(atom("1/2", "4/5")), image.atoms());
    assertEquals(Rational.ZERO, image.membershipAt(ExactPoint.of("1/4")));
    assertEquals(List.of(ExactPoint.of("1/2")), image.alphaCut(Rational.of(3, 4)));
    assertTrue(image.alphaCut(Rational.ONE).isEmpty());
    assertEquals(image, state(atom("1/2", "1/5"), atom("1/2", "4/5")));
    assertEquals(image.hashCode(), state(atom("1/2", "4/5")).hashCode());
    assertNotEquals(image, source);
    assertNotEquals(image, "another type");
    assertEquals(image, image);
    assertThrows(UnsupportedOperationException.class, () -> image.atoms().clear());
    assertTrue(new FiniteFuzzySet(1, List.of()).extend(new TentMap()).atoms().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> image.alphaCut(Rational.ZERO));
    assertThrows(IllegalArgumentException.class, () -> image.alphaCut(Rational.of(2)));
    assertThrows(IllegalArgumentException.class, () -> new FiniteFuzzySet(0, List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> source.maximum(new FiniteFuzzySet(2, List.of())));
    assertThrows(
        IllegalArgumentException.class, () -> source.extend(new CoupledTentMap(Rational.ZERO)));
    assertThrows(
        IllegalArgumentException.class, () -> new FuzzyAtom(ExactPoint.of("0"), Rational.of(-1)));
    assertThrows(
        IllegalArgumentException.class, () -> new FuzzyAtom(ExactPoint.of("0"), Rational.of(2)));
  }

  @Test
  void preservesMaximumAndPositiveAlphaCutImages() {
    Random random = new Random(118);
    TentMap map = new TentMap();
    for (int trial = 0; trial < 100; trial++) {
      FiniteFuzzySet left = randomState(random);
      FiniteFuzzySet right = randomState(random);
      assertEquals(left.extend(map).maximum(right.extend(map)), left.maximum(right).extend(map));
      for (int grade = 1; grade <= 4; grade++) {
        Rational alpha = Rational.of(grade, 4);
        TreeSet<ExactPoint> expected = new TreeSet<>();
        left.alphaCut(alpha).forEach(point -> expected.add(map.evaluateExact(point)));
        assertEquals(List.copyOf(expected), left.extend(map).alphaCut(alpha));
      }
    }
  }

  @Test
  void includesTheEntireZeroBaselineInTheMetric() {
    FiniteFuzzySet left = state(atom("0", "1/4"));
    FiniteFuzzySet right = state(atom("1", "1/4"));
    assertEquals(Rational.of(1, 4), EndographMetric.distance(left, right));
    assertEquals(Rational.of(1, 4), EndographMetric.distance(left, state()));
    assertEquals(Rational.ZERO, EndographMetric.distance(state(), state()));
    assertEquals(
        Rational.of(1, 2),
        EndographMetric.distance(state(atom("0", "1")), state(atom("0", "1/2"))));
    FiniteFuzzySet strong = state(atom("1/5", "1"), atom("3/10", "1/2"));
    FiniteFuzzySet weak = strong.maximum(state(atom("9/10", "1/100")));
    assertEquals(Rational.of(1, 100), EndographMetric.distance(strong, weak));
    assertEquals(3, weak.alphaCut(Rational.of(1, 200)).size());
    FiniteFuzzySet squareLeft =
        new FiniteFuzzySet(2, List.of(new FuzzyAtom(ExactPoint.of("0", "0"), Rational.ONE)));
    FiniteFuzzySet squareRight =
        new FiniteFuzzySet(2, List.of(new FuzzyAtom(ExactPoint.of("1/2", "1/2"), Rational.ONE)));
    assertEquals(Rational.of(1, 2), EndographMetric.distance(squareLeft, squareRight));
    assertThrows(IllegalArgumentException.class, () -> EndographMetric.distance(left, squareLeft));
  }

  @Test
  void matchesIndependentEnumeratedEndographsAndMetricProperties() {
    Random random = new Random(1201);
    for (int trial = 0; trial < 80; trial++) {
      FiniteFuzzySet a = randomState(random);
      FiniteFuzzySet b = randomState(random);
      FiniteFuzzySet c = randomState(random);
      Rational ab = EndographMetric.distance(a, b);
      assertEquals(bruteDistance(a, b), ab);
      assertEquals(ab, EndographMetric.distance(b, a));
      assertEquals(Rational.ZERO, EndographMetric.distance(a, a));
      assertTrue(
          ab.compareTo(EndographMetric.distance(a, c).add(EndographMetric.distance(c, b))) <= 0);
    }
  }

  private static Rational bruteDistance(FiniteFuzzySet a, FiniteFuzzySet b) {
    TreeSet<ExactPoint> positions = new TreeSet<>();
    positions.add(ExactPoint.of("0"));
    positions.add(ExactPoint.of("1"));
    a.atoms().forEach(atom -> positions.add(atom.location()));
    b.atoms().forEach(atom -> positions.add(atom.location()));
    List<ExactPoint> left = endographVertices(a, positions);
    List<ExactPoint> right = endographVertices(b, positions);
    return directedHausdorff(left, right).max(directedHausdorff(right, left));
  }

  private static List<ExactPoint> endographVertices(
      FiniteFuzzySet state, TreeSet<ExactPoint> positions) {
    List<ExactPoint> points = new ArrayList<>();
    // All test grades are multiples of 1/4. Clamp heights are therefore present in this reference.
    for (ExactPoint position : positions) {
      for (int height = 0; height <= 4; height++) {
        Rational grade = Rational.of(height, 4);
        if (state.membershipAt(position).compareTo(grade) >= 0) {
          points.add(new ExactPoint(List.of(position.coordinate(0), grade)));
        }
      }
    }
    return points;
  }

  private static Rational directedHausdorff(List<ExactPoint> a, List<ExactPoint> b) {
    Rational result = Rational.ZERO;
    for (ExactPoint point : a) {
      Rational nearest = Rational.ONE;
      for (ExactPoint candidate : b) {
        nearest = nearest.min(point.distanceInfinity(candidate));
      }
      result = result.max(nearest);
    }
    return result;
  }

  private static FiniteFuzzySet randomState(Random random) {
    List<FuzzyAtom> atoms = new ArrayList<>();
    for (int atom = 0; atom < 4; atom++) {
      atoms.add(
          new FuzzyAtom(
              new ExactPoint(List.of(Rational.of(random.nextInt(9), 8))),
              Rational.of(random.nextInt(5), 4)));
    }
    return new FiniteFuzzySet(1, atoms);
  }

  private static FuzzyAtom atom(String x, String grade) {
    return new FuzzyAtom(ExactPoint.of(x), Rational.parse(grade));
  }

  private static FiniteFuzzySet state(FuzzyAtom... atoms) {
    return new FiniteFuzzySet(1, List.of(atoms));
  }
}
