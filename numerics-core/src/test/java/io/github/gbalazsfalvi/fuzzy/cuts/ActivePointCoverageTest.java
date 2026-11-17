package io.github.gbalazsfalvi.fuzzy.cuts;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivePointCoverageTest {
  @Test
  void updatesExactCoverageBySplittingNeighborGapsAndCheckingOuterEndpoints() {
    ActivePointCoverage cover =
        new ActivePointCoverage(new IntervalUnion(List.of(CutGeometryTest.interval("0", "1"))));
    cover.insert(Rational.of(1, 4));
    assertEquals(Rational.of(3, 4), cover.maximumDistance());
    cover.insert(Rational.of(3, 4));
    assertEquals(Rational.of(1, 4), cover.maximumDistance());
    cover.insert(Rational.of(1, 2));
    cover.insert(Rational.ZERO);
    cover.insert(Rational.ONE);
    assertEquals(Rational.of(1, 8), cover.maximumDistance());
    cover.insert(Rational.of(1, 2));
    assertEquals(Rational.of(1, 8), cover.maximumDistance());
  }

  @Test
  void excludesGapPeaksOutsideTheActualNonconvexSource() {
    ActivePointCoverage cover =
        new ActivePointCoverage(
            new IntervalUnion(
                List.of(
                    CutGeometryTest.interval("0", "1/4"), CutGeometryTest.interval("3/4", "1"))));
    cover.insert(Rational.ZERO);
    cover.insert(Rational.ONE);
    assertEquals(Rational.of(1, 4), cover.maximumDistance());
    ActivePointCoverage empty = new ActivePointCoverage(new IntervalUnion(List.of()));
    empty.insert(Rational.ZERO);
    assertEquals(Rational.ZERO, empty.maximumDistance());
  }
}
