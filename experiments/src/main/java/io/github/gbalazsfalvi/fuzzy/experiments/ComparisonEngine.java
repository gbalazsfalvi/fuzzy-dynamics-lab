package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSnapshot;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CaeVerification;
import io.github.gbalazsfalvi.fuzzy.cae.CaeWork;
import io.github.gbalazsfalvi.fuzzy.cae.CertificateVerifier;
import io.github.gbalazsfalvi.fuzzy.cuts.AlphaCutTrajectory;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.pwl.ExactLinearMap;
import io.github.gbalazsfalvi.fuzzy.pwl.PiecewiseLinearExtension;
import io.github.gbalazsfalvi.fuzzy.pwl.PiecewiseLinearState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Algorithm execution is separate from clocks, diagnostics, serialization, and cohort selection.
 */
final class ComparisonEngine {
  static final CaeWork NO_INTERVAL_WORK = new CaeWork(0, 0, 0, 0, 0);

  private ComparisonEngine() {}

  static String exclusion(ComparisonProtocol.Method method, CaeProblem problem) {
    return switch (method.kind()) {
      case CAE -> null;
      case ALPHA_GRID ->
          problem.map().dimension() == 1
              ? null
              : "Exact interval union propagation is scalar; no joint two dimensional adapter is implemented.";
      case PUBLISHED_PWL_CORE ->
          problem.map() instanceof AffineContraction || problem.map() instanceof TentMap
              ? null
              : "Only the exact PWL transport specialization is reproduced; nonlinear PSO preprocessing is absent.";
    };
  }

  static CaeSettings settings(
      ComparisonProtocol protocol, ComparisonProtocol.Method method, BigDecimal tolerance) {
    return new CaeSettings(
        new DecimalArithmetic(protocol.precision()),
        tolerance,
        protocol.maxRefinements(),
        protocol.maxCells(),
        protocol.maxDepth(),
        protocol.fairnessPeriod(),
        method.strategy());
  }

  static Payload solve(
      ComparisonProtocol protocol,
      ComparisonProtocol.Method method,
      CaeProblem problem,
      BigDecimal tolerance) {
    if (exclusion(method, problem) != null) {
      throw new IllegalArgumentException("An ineligible method cannot be run on this problem.");
    }
    try {
      return switch (method.kind()) {
        case CAE ->
            new Payload(
                CaeEvidence.Outcome.from(
                    (method.strategy() != CaeSettings.Strategy.RANKED
                            ? new CaeSolver()
                            : new CaeSolver(
                                method.model() != null
                                    ? method.model()
                                    : new io.github.gbalazsfalvi.fuzzy.ranking.SeededPriority(
                                        method.randomSeed())))
                        .solve(problem, settings(protocol, method, tolerance))),
                null,
                null,
                null);
        case ALPHA_GRID ->
            new Payload(
                null, AlphaCutTrajectory.compute(problem, levelsFor(tolerance)), null, null);
        case PUBLISHED_PWL_CORE ->
            new Payload(null, null, linearTrajectory(problem, protocol.maxPwlPieces()), null);
      };
    } catch (ArithmeticException exception) {
      return new Payload(null, null, null, "Verified arithmetic failed: " + exception);
    }
  }

  static Verification verify(
      ComparisonProtocol protocol,
      ComparisonProtocol.Method method,
      CaeProblem problem,
      BigDecimal tolerance,
      Payload payload) {
    if (payload.failure() != null) {
      return new Verification(NO_INTERVAL_WORK, 0, 0);
    }
    if (method.kind() == ComparisonProtocol.Kind.CAE) {
      if (payload.cae() == null) {
        throw new IllegalArgumentException("The method must carry a CAE outcome.");
      }
      CaeResult result = payload.cae().toCore();
      CaeWork work = NO_INTERVAL_WORK;
      List<CaeSnapshot> snapshots = new ArrayList<>();
      result.best().ifPresent(snapshots::add);
      result
          .last()
          .filter(last -> !result.best().orElseThrow().equals(last))
          .ifPresent(snapshots::add);
      for (CaeSnapshot snapshot : snapshots) {
        CaeVerification verification =
            new CertificateVerifier()
                .verify(problem, settings(protocol, method, tolerance), snapshot);
        if (!verification.valid()) {
          throw new IllegalArgumentException("CAE certificate rejected: " + verification.issues());
        }
        work = add(work, verification.work());
      }
      if (result.status() == CaeResult.Status.CERTIFIED
          && result.best().orElseThrow().worstBound().compareTo(tolerance) > 0) {
        throw new IllegalArgumentException(
            "A certified outcome must meet the requested tolerance.");
      }
      return new Verification(work, 0, 0);
    }
    if (method.kind() == ComparisonProtocol.Kind.ALPHA_GRID) {
      AlphaCutTrajectory expected = AlphaCutTrajectory.compute(problem, levelsFor(tolerance));
      if (!expected.equals(payload.alpha())) {
        throw new IllegalArgumentException(
            "The saved exact alpha trajectory differs from recomputation.");
      }
      return new Verification(
          NO_INTERVAL_WORK, expected.exactPointEvaluations(), expected.intervalImages());
    }
    if (payload.linear() == null) {
      throw new IllegalArgumentException("The PWL method must carry complete prefix states.");
    }
    // Exact cut images provide an independent parity construction for the transported linear
    // pieces.
    // Finite alpha parity supplements the exact transport argument; it is not a proof at all
    // levels.
    AlphaCutTrajectory reference = AlphaCutTrajectory.compute(problem, diagnosticLevels(protocol));
    LinearOutcome output = payload.linear();
    if (output.states().isEmpty()
        || output.states().size() > problem.horizon() + 1
        || (output.complete() && output.states().size() != problem.horizon() + 1)) {
      throw new IllegalArgumentException(
          "The PWL result must declare its complete horizon or prefix.");
    }
    for (int time = 0; time < output.states().size(); time++) {
      for (int level = 0; level < reference.levels().size(); level++) {
        if (!output
            .states()
            .get(time)
            .cut(reference.levels().get(level))
            .equals(reference.states().get(time).get(level))) {
          throw new IllegalArgumentException(
              "PWL transport disagrees with an independent exact alpha cut.");
        }
      }
    }
    return new Verification(
        NO_INTERVAL_WORK, reference.exactPointEvaluations(), reference.intervalImages());
  }

  static List<Rational> diagnosticLevels(ComparisonProtocol protocol) {
    List<Rational> levels = new ArrayList<>(protocol.diagnosticLevels());
    levels.add(Rational.ONE);
    return levels.stream().distinct().sorted().toList();
  }

  private static List<Rational> levelsFor(BigDecimal tolerance) {
    int count = BigDecimal.ONE.divide(tolerance, 0, RoundingMode.CEILING).intValueExact();
    return AlphaCutTrajectory.uniformLevels(count);
  }

  private static LinearOutcome linearTrajectory(CaeProblem problem, int maxPieces) {
    List<PiecewiseLinearState> states = new ArrayList<>();
    PiecewiseLinearState state = PiecewiseLinearState.from(problem.membership());
    states.add(state);
    long evaluations = 0;
    for (int time = 1; time <= problem.horizon(); time++) {
      try {
        PiecewiseLinearExtension.Transport next =
            PiecewiseLinearExtension.extend(state, ExactLinearMap.from(problem.map()), maxPieces);
        state = next.state();
        evaluations = Math.addExact(evaluations, next.exactPointEvaluations());
        states.add(state);
      } catch (PiecewiseLinearExtension.PieceLimitException exception) {
        evaluations = Math.addExact(evaluations, exception.exactPointEvaluations());
        return new LinearOutcome(states, evaluations, false, "MAX_PWL_PIECES");
      }
    }
    return new LinearOutcome(states, evaluations, true, "EXACT_PWL_TRANSPORT");
  }

  private static CaeWork add(CaeWork first, CaeWork second) {
    return new CaeWork(
        Math.addExact(first.cellAttempts(), second.cellAttempts()),
        Math.addExact(first.mapImages(), second.mapImages()),
        Math.addExact(first.lipschitzBounds(), second.lipschitzBounds()),
        Math.addExact(first.membershipRanges(), second.membershipRanges()),
        Math.addExact(first.membershipLipschitzBounds(), second.membershipLipschitzBounds()));
  }

  record Payload(
      CaeEvidence.Outcome cae, AlphaCutTrajectory alpha, LinearOutcome linear, String failure) {
    Payload {
      if ((cae == null ? 0 : 1)
              + (alpha == null ? 0 : 1)
              + (linear == null ? 0 : 1)
              + (failure == null ? 0 : 1)
          != 1) {
        throw new IllegalArgumentException("A numerical result needs exactly one typed outcome.");
      }
    }

    String status() {
      if (failure != null) {
        return "NUMERICAL_FAILURE";
      }
      if (cae != null) {
        return cae.status().name();
      }
      return linear == null || linear.complete() ? "CERTIFIED" : "BUDGET_EXHAUSTED";
    }

    String reason() {
      return failure != null
          ? failure
          : cae != null
              ? cae.reason().name()
              : linear != null ? linear.reason() : "EXACT_CUTS_WITH_VERTICAL_QUANTIZATION";
    }

    Rational bound() {
      if (cae != null) {
        return cae.best() == null ? null : Rational.fromDecimal(cae.best().worstBound());
      }
      return alpha != null
          ? alpha.errorBound()
          : linear != null && linear.complete() ? Rational.ZERO : null;
    }

    int geometrySize() {
      if (cae != null) {
        return cae.last() == null ? 0 : cae.last().cells().size();
      }
      if (alpha != null) {
        return alpha.states().getLast().stream().mapToInt(cut -> cut.intervals().size()).sum();
      }
      return linear == null ? 0 : linear.states().getLast().pieces().size();
    }

    CaeWork intervalWork() {
      return cae == null ? NO_INTERVAL_WORK : cae.work();
    }

    long exactPointEvaluations() {
      return alpha != null
          ? alpha.exactPointEvaluations()
          : linear != null ? linear.exactPointEvaluations() : 0;
    }
  }

  record LinearOutcome(
      List<PiecewiseLinearState> states,
      long exactPointEvaluations,
      boolean complete,
      String reason) {
    LinearOutcome {
      states = List.copyOf(states);
      if (states.isEmpty()
          || exactPointEvaluations < 0
          || !(complete ? "EXACT_PWL_TRANSPORT" : "MAX_PWL_PIECES").equals(reason)) {
        throw new IllegalArgumentException(
            "A PWL outcome needs valid complete prefix states and a reason.");
      }
    }
  }

  record Verification(CaeWork intervalWork, long exactPointEvaluations, long intervalImages) {}
}
