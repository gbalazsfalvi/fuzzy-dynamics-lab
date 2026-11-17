package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.ranking.DenseRankingModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Executable versioned cohorts with explicit pilot, validation, and frozen held out stages. */
record ComparisonProtocol(
    String id,
    String date,
    String stage,
    int precision,
    List<BigDecimal> tolerances,
    int maxRefinements,
    int maxCells,
    int maxDepth,
    int fairnessPeriod,
    int maxPwlPieces,
    int warmupRepetitions,
    int timingRepetitions,
    long orderSeed,
    int referenceLevels,
    List<Rational> diagnosticLevels,
    List<Method> methods,
    List<Problem> problems) {
  ComparisonProtocol {
    if (id == null
        || !id.matches("[a-z0-9-]+")
        || !List.of("EXPLORATORY_PILOT", "VALIDATION_ONLY", "FROZEN_HELD_OUT").contains(stage)) {
      throw new IllegalArgumentException("A comparison needs a named exploratory pilot protocol.");
    }
    LocalDate.parse(date);
    tolerances = List.copyOf(tolerances);
    diagnosticLevels = List.copyOf(diagnosticLevels);
    methods = List.copyOf(methods);
    problems = List.copyOf(problems);
    if (precision < 1
        || maxRefinements < 0
        || maxCells < 1
        || maxDepth < 0
        || fairnessPeriod < 1
        || maxPwlPieces < 1
        || warmupRepetitions < 1
        || timingRepetitions < 5
        || referenceLevels < 1
        || tolerances.isEmpty()
        || methods.isEmpty()
        || problems.isEmpty()
        || diagnosticLevels.isEmpty()) {
      throw new IllegalArgumentException(
          "The pilot needs valid budgets, warmup, and five timed repetitions.");
    }
    for (BigDecimal tolerance : tolerances) {
      if (tolerance.signum() <= 0 || tolerance.compareTo(BigDecimal.ONE) >= 0) {
        throw new IllegalArgumentException(
            "Comparison tolerances must be strictly between zero and one.");
      }
    }
    for (Rational alpha : diagnosticLevels) {
      if (alpha.compareTo(Rational.ZERO) <= 0 || alpha.compareTo(Rational.ONE) > 0) {
        throw new IllegalArgumentException("Diagnostic levels must be positive unit grades.");
      }
    }
    if (methods.stream().map(Method::id).distinct().count() != methods.size()
        || problems.stream().map(Problem::id).distinct().count() != problems.size()
        || tolerances.stream().map(BigDecimal::stripTrailingZeros).distinct().count()
            != tolerances.size()) {
      throw new IllegalArgumentException(
          "Methods, problems, and tolerances must be uniquely identified.");
    }
  }

  boolean timed(Problem problem) {
    return stage.equals("EXPLORATORY_PILOT") || problem.timingCohort();
  }

  int repetitions(Problem problem) {
    return timed(problem) ? timingRepetitions : 1;
  }

  int warmups(Problem problem) {
    return timed(problem) ? warmupRepetitions : 0;
  }

  record Method(
      String id,
      Kind kind,
      CaeSettings.Strategy strategy,
      DenseRankingModel model,
      Long randomSeed) {
    Method(String id, Kind kind, CaeSettings.Strategy strategy) {
      this(id, kind, strategy, null, null);
    }

    Method(String id, Kind kind, CaeSettings.Strategy strategy, DenseRankingModel model) {
      this(id, kind, strategy, model, null);
    }

    Method {
      if (id == null || !id.matches("[a-z0-9-]+")) {
        throw new IllegalArgumentException("A method needs a safe stable identifier.");
      }
      Objects.requireNonNull(kind, "kind");
      if ((kind == Kind.CAE) != (strategy != null)) {
        throw new IllegalArgumentException("Only CAE methods have an engine strategy.");
      }
      int rankers = (model == null ? 0 : 1) + (randomSeed == null ? 0 : 1);
      if (strategy == CaeSettings.Strategy.RANKED ? rankers != 1 : rankers != 0) {
        throw new IllegalArgumentException(
            "Ranked comparison methods must bind their complete frozen model.");
      }
    }
  }

  enum Kind {
    CAE,
    ALPHA_GRID,
    PUBLISHED_PWL_CORE
  }

  record Problem(
      String id,
      String baseProblemId,
      boolean qualifiedReference,
      CaeEvidence.Problem definition,
      boolean timingCohort) {
    Problem(
        String id,
        String baseProblemId,
        boolean qualifiedReference,
        CaeEvidence.Problem definition) {
      this(id, baseProblemId, qualifiedReference, definition, true);
    }

    Problem {
      if (id == null
          || !id.matches("[a-z0-9-]+")
          || baseProblemId == null
          || !baseProblemId.matches("[a-z0-9-]+")) {
        throw new IllegalArgumentException(
            "Problem and grouping identifiers must be safe and stable.");
      }
      Objects.requireNonNull(definition, "definition");
      definition.toCore();
      if (qualifiedReference && definition.toCore().map().dimension() != 1) {
        throw new IllegalArgumentException(
            "The current independent continuous reference is scalar.");
      }
    }
  }
}
