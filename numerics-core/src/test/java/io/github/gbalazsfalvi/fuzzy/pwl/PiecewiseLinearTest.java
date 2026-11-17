package io.github.gbalazsfalvi.fuzzy.pwl;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cuts.ExactAlphaCuts;
import io.github.gbalazsfalvi.fuzzy.cuts.IntervalUnion;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.CertifiedMembership;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiecewiseLinearTest {
  private static final List<Rational> LEVELS =
      List.of(
          Rational.of(1, 200),
          Rational.of(1, 100),
          Rational.of(1, 20),
          Rational.of(1, 4),
          Rational.of(1, 2),
          Rational.of(3, 4),
          Rational.ONE);

  @Test
  void representsPeaksAndEveryPositiveCutExactlyIncludingBoundaryPeaks() {
    for (CertifiedMembership profile :
        List.of(
            new TentPeak(ExactPoint.of("0"), Rational.of(3, 2), Rational.ONE),
            new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 7), Rational.of(2, 3)),
            new MultiPeakMembership(
                List.of(
                    new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.ONE),
                    new TentPeak(ExactPoint.of("3/4"), Rational.of(1, 8), Rational.of(1, 100)))))) {
      PiecewiseLinearState state = PiecewiseLinearState.from(profile);
      for (int index = 0; index <= 64; index++) {
        Rational coordinate = Rational.of(index, 64);
        assertEquals(
            profile.evaluateExact(new ExactPoint(List.of(coordinate))), state.at(coordinate));
      }
      for (Rational alpha : LEVELS) {
        assertEquals(ExactAlphaCuts.initial(profile, alpha), state.cut(alpha));
      }
    }
  }

  @Test
  void reproducesExactTransportCutsForAffineTentAndConstantMaps() {
    CertifiedMembership profile =
        new MultiPeakMembership(
            List.of(
                new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.ONE),
                new TentPeak(ExactPoint.of("3/4"), Rational.of(1, 8), Rational.of(1, 100))));
    for (CertifiedMap map :
        List.of(
            new AffineContraction(Rational.of(3, 4)),
            new AffineContraction(Rational.ZERO),
            new TentMap())) {
      ExactLinearMap linear = ExactLinearMap.from(map);
      PiecewiseLinearState state = PiecewiseLinearState.from(profile);
      List<IntervalUnion> cuts =
          LEVELS.stream().map(alpha -> ExactAlphaCuts.initial(profile, alpha)).toList();
      for (int time = 0; time <= 4; time++) {
        for (int level = 0; level < LEVELS.size(); level++) {
          assertEquals(cuts.get(level), state.cut(LEVELS.get(level)));
        }
        PiecewiseLinearExtension.Transport output =
            PiecewiseLinearExtension.extend(state, linear, 1024);
        assertEquals(2L * output.state().pieces().size(), output.exactPointEvaluations());
        state = output.state();
        cuts = cuts.stream().map(cut -> ExactAlphaCuts.image(map, cut).cuts()).toList();
      }
    }
  }

  @Test
  void reproducesThePublishedExampleSixTransportKernelWithoutPso() {
    // Four map vertices explicitly listed in Kupka and Skorupova (2021), Example 6.
    ExactLinearMap map =
        new ExactLinearMap(
            List.of(knot("0", "0"), knot("1/8", "3/4"), knot("2/5", "3/5"), knot("1", "0")));
    List<LinearKnot> membership =
        List.of(
            knot("0", "0"),
            knot("1/5", "0"),
            knot("2/5", "3/5"),
            knot("4/5", "1"),
            knot("9/10", "0"),
            knot("1", "0"));
    List<LinearSegment> pieces = new ArrayList<>();
    for (int index = 1; index < membership.size(); index++) {
      LinearKnot left = membership.get(index - 1);
      LinearKnot right = membership.get(index);
      pieces.add(
          new LinearSegment(
              new RationalInterval(left.coordinate(), right.coordinate()),
              left.value(),
              right.value()));
    }
    PiecewiseLinearState state = new PiecewiseLinearState(pieces);
    List<IntervalUnion> independent = LEVELS.stream().map(state::cut).toList();
    for (int time = 0; time < 6; time++) {
      for (int level = 0; level < LEVELS.size(); level++) {
        assertEquals(independent.get(level), state.cut(LEVELS.get(level)));
      }
      independent = independent.stream().map(cut -> independentImage(map, cut)).toList();
      state = PiecewiseLinearExtension.extend(state, map, 4096).state();
    }
  }

  @Test
  void retainsFoldMaximaSingletonSpikesAndSupportJumps() {
    PiecewiseLinearState profile =
        PiecewiseLinearState.from(
            new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.of(3, 4)));
    PiecewiseLinearState constant =
        PiecewiseLinearExtension.extend(
                profile, ExactLinearMap.from(new AffineContraction(Rational.ZERO)), 16)
            .state();
    assertEquals(Rational.of(3, 4), constant.at(Rational.of(1, 2)));
    assertEquals(Rational.ZERO, constant.at(Rational.of(49, 100)));
    PiecewiseLinearState spike =
        new PiecewiseLinearState(
            List.of(
                new LinearSegment(interval("1/2", "1/2"), Rational.of(1, 4), Rational.of(3, 4))));
    PiecewiseLinearState transported =
        PiecewiseLinearExtension.extend(spike, ExactLinearMap.from(new TentMap()), 8).state();
    assertEquals(Rational.of(3, 4), transported.at(Rational.ONE));
    assertEquals(Rational.ZERO, transported.at(Rational.of(99, 100)));
    assertEquals(List.of(interval("1", "1")), transported.cut(Rational.of(1, 2)).intervals());
    assertTrue(transported.cut(Rational.ONE).intervals().isEmpty());
  }

  @Test
  void abandonsIncompletePieceOutputsAndRetainsActualAttemptedWork() {
    PiecewiseLinearState state =
        PiecewiseLinearState.from(
            new TentPeak(ExactPoint.of("1/2"), Rational.of(1, 4), Rational.ONE));
    ExactLinearMap map = ExactLinearMap.from(new TentMap());
    PiecewiseLinearExtension.PieceLimitException failure =
        assertThrows(
            PiecewiseLinearExtension.PieceLimitException.class,
            () -> PiecewiseLinearExtension.extend(state, map, 1));
    assertEquals(1, failure.completedPieces());
    assertEquals(4, failure.exactPointEvaluations());
    assertEquals(Rational.ONE, state.at(Rational.of(1, 2)));
    assertThrows(
        IllegalArgumentException.class, () -> PiecewiseLinearExtension.extend(state, map, 0));
    assertEquals(
        0,
        PiecewiseLinearExtension.extend(new PiecewiseLinearState(List.of()), map, 1)
            .exactPointEvaluations());
    assertThrows(
        IllegalArgumentException.class, () -> new PiecewiseLinearExtension.Transport(state, -1));
  }

  @Test
  void validatesGraphVerticesFullMapsAndMembershipSegments() {
    assertThrows(IllegalArgumentException.class, () -> knot("-1", "0"));
    assertThrows(IllegalArgumentException.class, () -> knot("2", "0"));
    assertThrows(IllegalArgumentException.class, () -> knot("0", "-1"));
    assertThrows(IllegalArgumentException.class, () -> knot("0", "2"));
    for (List<LinearKnot> knots :
        List.of(
            List.<LinearKnot>of(),
            List.of(knot("0", "0")),
            List.of(knot("1/4", "0"), knot("1", "0")),
            List.of(knot("0", "0"), knot("3/4", "0")),
            List.of(knot("0", "0"), knot("1/2", "0"), knot("1/2", "1"), knot("1", "0")))) {
      assertThrows(IllegalArgumentException.class, () -> new ExactLinearMap(knots));
    }
    ExactLinearMap map = ExactLinearMap.from(new TentMap());
    assertThrows(IllegalArgumentException.class, () -> map.at(Rational.of(-1)));
    assertThrows(IllegalArgumentException.class, () -> map.at(Rational.of(2)));
    assertThrows(
        IllegalArgumentException.class, () -> ExactLinearMap.from(new LogisticMap(Rational.ONE)));
    assertThrows(
        IllegalArgumentException.class,
        () -> ExactLinearMap.from(new CoupledTentMap(Rational.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PiecewiseLinearState.from(
                new TentPeak(ExactPoint.of("1/2", "1/2"), Rational.ONE, Rational.ONE)));
    for (List<Rational> grades :
        List.of(
            List.of(Rational.of(-1), Rational.ZERO),
            List.of(Rational.of(2), Rational.ZERO),
            List.of(Rational.ZERO, Rational.of(-1)),
            List.of(Rational.ZERO, Rational.of(2)))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new LinearSegment(interval("0", "1"), grades.get(0), grades.get(1)));
    }
    PiecewiseLinearState zero = new PiecewiseLinearState(List.of());
    assertEquals(Rational.ZERO, zero.at(Rational.of(1, 2)));
    assertTrue(zero.cut(Rational.ONE).intervals().isEmpty());
    LinearSegment rising = new LinearSegment(interval("1/4", "3/4"), Rational.ZERO, Rational.ONE);
    LinearSegment falling = new LinearSegment(interval("1/4", "3/4"), Rational.ONE, Rational.ZERO);
    assertEquals(Rational.ZERO, rising.at(Rational.ZERO));
    assertEquals(Rational.of(1, 2), rising.at(Rational.of(1, 2)));
    assertEquals(interval("1/2", "3/4"), rising.cut(Rational.of(1, 2)).orElseThrow());
    assertEquals(interval("1/4", "1/2"), falling.cut(Rational.of(1, 2)).orElseThrow());
    for (Rational alpha : List.of(Rational.ZERO, Rational.of(2))) {
      assertThrows(IllegalArgumentException.class, () -> rising.cut(alpha));
      assertThrows(IllegalArgumentException.class, () -> zero.cut(alpha));
    }
  }

  private static IntervalUnion independentImage(ExactLinearMap map, IntervalUnion source) {
    List<RationalInterval> output = new ArrayList<>();
    for (RationalInterval interval : source.intervals()) {
      List<Rational> images =
          new ArrayList<>(List.of(map.at(interval.lower()), map.at(interval.upper())));
      for (LinearKnot knot : map.knots()) {
        if (interval.contains(knot.coordinate())) {
          images.add(knot.value());
        }
      }
      output.add(
          new RationalInterval(
              images.stream().min(Rational::compareTo).orElseThrow(),
              images.stream().max(Rational::compareTo).orElseThrow()));
    }
    return new IntervalUnion(output);
  }

  private static LinearKnot knot(String coordinate, String value) {
    return new LinearKnot(Rational.parse(coordinate), Rational.parse(value));
  }

  private static RationalInterval interval(String left, String right) {
    return new RationalInterval(Rational.parse(left), Rational.parse(right));
  }
}
