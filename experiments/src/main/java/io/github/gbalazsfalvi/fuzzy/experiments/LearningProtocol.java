package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.ranking.CellFeatures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Frozen base-problem grouping, with test definitions carried but never labeled by the exporter.
 */
record LearningProtocol(
    String id,
    String date,
    String stage,
    long generationSeed,
    int precision,
    int labelMaxRefinements,
    int labelMaxCells,
    int labelMaxDepth,
    int fairnessPeriod,
    BigDecimal labelTolerance,
    List<Long> modelSeeds,
    String featureVersion,
    String primaryTarget,
    String selectionRule,
    List<Problem> problems) {
  LearningProtocol {
    LocalDate.parse(date);
    modelSeeds = List.copyOf(modelSeeds);
    problems = List.copyOf(problems);
    if (!"FROZEN_BEFORE_LABELS_AND_FINAL_EVALUATION".equals(stage)
        || !CellFeatures.VERSION.equals(featureVersion)
        || id == null
        || !id.matches("[a-z0-9-]+")
        || precision < 1
        || labelMaxRefinements < 0
        || labelMaxCells < 1
        || labelMaxDepth < 0
        || fairnessPeriod < 1
        || labelTolerance.signum() <= 0
        || labelTolerance.compareTo(BigDecimal.ONE) >= 0
        || modelSeeds.size() < 5
        || modelSeeds.stream().distinct().count() != modelSeeds.size()
        || problems.isEmpty()
        || problems.stream().map(Problem::baseProblemId).distinct().count() != problems.size()) {
      throw new IllegalArgumentException(
          "The learning manifest must have valid frozen groups and budgets.");
    }
  }

  record Problem(String id, String baseProblemId, String split, CaeEvidence.Problem definition) {
    Problem {
      if (id == null
          || !id.matches("[a-z0-9-]+")
          || baseProblemId == null
          || !baseProblemId.matches("[a-z0-9-]+")
          || !List.of(
                  "train", "validation", "test-id-1d", "test-id-2d", "test-ood-1d", "test-ood-2d")
              .contains(split)) {
        throw new IllegalArgumentException(
            "Base problems require safe identities and explicit disjoint splits.");
      }
      definition.toCore();
    }

    boolean labelEligible() {
      return split.equals("train") || split.equals("validation");
    }
  }
}
