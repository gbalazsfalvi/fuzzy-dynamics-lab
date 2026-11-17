package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import io.github.gbalazsfalvi.fuzzy.ranking.CellFeatures;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningDataTest {
  @TempDir Path directory;

  @Test
  void labelsOnlyTrainingAndValidationGroupsAndRecordsTrialCosts() throws IOException {
    Path source = directory.resolve("source/numerics-core/src/main/java");
    Files.createDirectories(source);
    Files.writeString(source.resolve("Example.java"), "class Example {}\n");
    Path output = directory.resolve("labels");
    LearningProtocol protocol = fixture();
    LearningData.Summary first =
        LearningData.export(directory.resolve("source"), protocol, output, ignored -> {});
    assertEquals(2, first.problems().size());
    assertTrue(first.problems().stream().noneMatch(p -> p.id().contains("hidden")));
    assertEquals(10, first.rows());
    assertEquals(20, first.labelWork().cellAttempts());
    assertEquals(80, first.labelWork().mapImages());
    String train = Files.readString(output.resolve("train.csv"));
    assertFalse(train.contains("hidden"));
    assertTrue(train.contains("signedGain"));
    assertEquals(6, train.lines().count());
    assertEquals(6, Files.readString(output.resolve("validation.csv")).lines().count());
    LearningData.Summary second =
        LearningData.export(
            directory.resolve("source"), protocol, directory.resolve("repeat"), ignored -> {});
    assertEquals(first, second);
    assertEquals(train, Files.readString(directory.resolve("repeat/train.csv")));
  }

  static LearningProtocol fixture() {
    CaeEvidence.Problem definition =
        CaeEvidence.Problem.from(
            new CaeProblem(
                new TentMap(),
                new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 5), Rational.ONE),
                2));
    return new LearningProtocol(
        "test-learning",
        "2026-11-17",
        "FROZEN_BEFORE_LABELS_AND_FINAL_EVALUATION",
        42,
        8,
        2,
        3,
        8,
        5,
        new BigDecimal("0.000001"),
        List.of(11L, 23L, 37L, 51L, 71L),
        CellFeatures.VERSION,
        "signed gain",
        "gain per cost",
        List.of(
            new LearningProtocol.Problem("train-case", "train-case", "train", definition),
            new LearningProtocol.Problem(
                "validation-case", "validation-case", "validation", definition),
            new LearningProtocol.Problem("hidden-test", "hidden-test", "test-id-1d", definition)));
  }
}
