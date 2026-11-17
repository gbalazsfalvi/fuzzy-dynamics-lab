package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSnapshot;
import io.github.gbalazsfalvi.fuzzy.cuts.AlphaCutTrajectory;
import io.github.gbalazsfalvi.fuzzy.cuts.AlphaGridEndograph;
import io.github.gbalazsfalvi.fuzzy.cuts.CutComparison;
import io.github.gbalazsfalvi.fuzzy.cuts.IntervalUnion;
import io.github.gbalazsfalvi.fuzzy.cuts.RationalInterval;
import java.util.ArrayList;
import java.util.List;

/** Qualified continuous references and cut diagnostics run outside the timed numerical solve. */
final class ComparisonDiagnostics {
  private ComparisonDiagnostics() {}

  static References prepare(ComparisonProtocol protocol, ComparisonProtocol.Problem definition) {
    CaeProblem problem = definition.definition().toCore();
    if (problem.map().dimension() != 1) {
      return new References(null, null);
    }
    AlphaCutTrajectory cuts =
        AlphaCutTrajectory.compute(problem, ComparisonEngine.diagnosticLevels(protocol));
    AlphaCutTrajectory dense =
        definition.qualifiedReference()
            ? AlphaCutTrajectory.compute(
                problem, AlphaCutTrajectory.uniformLevels(protocol.referenceLevels()))
            : null;
    return new References(cuts, dense);
  }

  static Data measure(ComparisonEngine.Payload payload, References references) {
    if (references.cuts() == null) {
      return new Data("UNAVAILABLE_JOINT_TWO_DIMENSIONAL_REFERENCE", List.of(), List.of());
    }
    if (payload.failure() != null || (payload.cae() != null && payload.cae().best() == null)) {
      return new Data("NO_COMPLETE_APPROXIMATION", List.of(), List.of());
    }
    CaeSnapshot snapshot = payload.cae() == null ? null : payload.cae().best();
    int times =
        snapshot != null
            ? snapshot.bounds().size()
            : payload.alpha() != null
                ? payload.alpha().states().size()
                : payload.linear().states().size();
    List<CutDiagnostic> cuts = new ArrayList<>();
    List<ReferenceError> errors = new ArrayList<>();
    for (int time = 0; time < times; time++) {
      for (int level = 0; level < references.cuts().levels().size(); level++) {
        Rational alpha = references.cuts().levels().get(level);
        IntervalUnion reference = references.cuts().states().get(time).get(level);
        IntervalUnion approximation = approximationCut(payload, time, alpha);
        CutComparison compared = CutComparison.compare(reference, approximation);
        cuts.add(
            new CutDiagnostic(
                time,
                alpha,
                compared.status().name(),
                compared.distance(),
                reference.intervals().size(),
                approximation.intervals().size()));
      }
      if (snapshot != null && references.dense() != null) {
        Rational measured =
            AlphaGridEndograph.distance(snapshot.approximation(time), references.dense(), time);
        Rational delta = references.dense().errorBound();
        Rational lower = measured.subtract(delta).max(Rational.ZERO);
        Rational upper = measured.add(delta).min(Rational.ONE);
        Rational certificate = Rational.fromDecimal(snapshot.bounds().get(time));
        if (lower.compareTo(certificate) > 0) {
          throw new IllegalStateException(
              "A qualified continuous error lower bound exceeds the CAE certificate.");
        }
        errors.add(
            new ReferenceError(
                time, "VERIFIED_ENCLOSURE", measured, delta, lower, upper, certificate));
      }
    }
    String status =
        snapshot == null
            ? "EXACT_CUT_DIAGNOSTICS_METHOD_BOUND_SEPARATE"
            : references.dense() == null
                ? "EXACT_CUTS_CONTINUOUS_REFERENCE_NOT_SCHEDULED"
                : "QUALIFIED_CONTINUOUS_ERROR_BRACKETS";
    return new Data(status, errors, cuts);
  }

  private static IntervalUnion approximationCut(
      ComparisonEngine.Payload payload, int time, Rational alpha) {
    if (payload.cae() != null) {
      return new IntervalUnion(
          payload.cae().best().approximation(time).alphaCut(alpha).stream()
              .map(point -> new RationalInterval(point.coordinate(0), point.coordinate(0)))
              .toList());
    }
    if (payload.linear() != null) {
      return payload.linear().states().get(time).cut(alpha);
    }
    AlphaCutTrajectory grid = payload.alpha();
    for (int index = 0; index < grid.levels().size(); index++) {
      if (grid.levels().get(index).compareTo(alpha) >= 0) {
        return grid.states().get(time).get(index);
      }
    }
    throw new AssertionError("A validated staircase grid always includes level one.");
  }

  record References(AlphaCutTrajectory cuts, AlphaCutTrajectory dense) {}

  record Data(
      String referenceStatus, List<ReferenceError> referenceErrors, List<CutDiagnostic> cuts) {
    Data {
      referenceErrors = List.copyOf(referenceErrors);
      cuts = List.copyOf(cuts);
    }
  }

  record ReferenceError(
      int time,
      String quality,
      Rational distanceToStaircase,
      Rational referenceError,
      Rational lower,
      Rational upper,
      Rational certificate) {}

  record CutDiagnostic(
      int time,
      Rational alpha,
      String status,
      Rational hausdorff,
      int referenceIntervals,
      int approximationElements) {}
}
