package io.github.gbalazsfalvi.fuzzy.ranking;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CertificateVerifier;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SeededPriorityTest {
  @Test
  void aSeedDefinesReproducibleChoicesWhileBoundsRemainIndependentlyValid() {
    CaeProblem problem =
        new CaeProblem(
            new TentMap(), new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 5), Rational.ONE), 4);
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(16),
            new BigDecimal("0.01"),
            18,
            19,
            12,
            5,
            CaeSettings.Strategy.RANKED);
    var first = new CaeSolver(new SeededPriority(17)).solve(problem, settings);
    var replay = new CaeSolver(new SeededPriority(17)).solve(problem, settings);
    var other = new CaeSolver(new SeededPriority(18)).solve(problem, settings);
    assertEquals(first, replay);
    assertNotEquals(first.history(), other.history());
    assertTrue(
        new CertificateVerifier().verify(problem, settings, first.last().orElseThrow()).valid());
    assertTrue(
        first.history().stream()
            .anyMatch(
                event ->
                    event.refinement() == 5
                        && event.selection()
                            == io.github.gbalazsfalvi.fuzzy.cae.RefinementEvent.Selection
                                .FAIRNESS));
  }
}
