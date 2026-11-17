package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.cae.CaeCell;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CaeWork;
import io.github.gbalazsfalvi.fuzzy.cae.CellPath;
import io.github.gbalazsfalvi.fuzzy.cae.SplitProbe;
import io.github.gbalazsfalvi.fuzzy.ranking.CellFeatures;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bounded trial-split labels from train/validation problems only; no final test geometry is solved.
 */
final class LearningData {
  private LearningData() {}

  static Summary export(
      Path sourceRoot, LearningProtocol protocol, Path output, Consumer<String> progress)
      throws IOException {
    Files.createDirectories(output);
    Map<String, StringBuilder> csv = new LinkedHashMap<>();
    for (String split : List.of("train", "validation")) {
      csv.put(
          split,
          new StringBuilder("baseProblemId,path,split,")
              .append(String.join(",", CellFeatures.names()))
              .append(",signedGain,parentBound,childrenBound,estimatedChildWork\n"));
    }
    List<ProblemCost> costs = new ArrayList<>();
    CaeWork totalLabels = new CaeWork(0, 0, 0, 0, 0);
    long rows = 0;
    for (LearningProtocol.Problem definition : protocol.problems()) {
      if (!definition.labelEligible()) {
        continue;
      }
      var problem = definition.definition().toCore();
      CaeSettings settings =
          new CaeSettings(
              new DecimalArithmetic(protocol.precision()),
              protocol.labelTolerance(),
              protocol.labelMaxRefinements(),
              protocol.labelMaxCells(),
              protocol.labelMaxDepth(),
              protocol.fairnessPeriod(),
              CaeSettings.Strategy.RANKED);
      Map<CellPath, CaeCell> candidates = new LinkedHashMap<>();
      CaeResult exploration =
          new CaeSolver(
                  cell -> {
                    candidates.put(cell.path(), cell);
                    return cell.worstBound();
                  })
              .solve(problem, settings);
      if (exploration.last().isEmpty()) {
        throw new IllegalStateException(
            "Training exploration did not initialize a complete partition.");
      }
      CaeWork work = new CaeWork(0, 0, 0, 0, 0);
      for (CaeCell parent : candidates.values()) {
        SplitProbe trial = SplitProbe.evaluate(problem, settings, parent.path());
        BigDecimal gain = trial.gainAgainst(parent);
        StringBuilder line = csv.get(definition.split());
        line.append(definition.baseProblemId())
            .append(',')
            .append(parent.path().toString())
            .append(',')
            .append(definition.split());
        for (double feature : CellFeatures.of(parent)) {
          line.append(',').append(Double.toString(feature));
        }
        line.append(',')
            .append(gain.toPlainString())
            .append(',')
            .append(parent.worstBound().toPlainString())
            .append(',')
            .append(trial.left().worstBound().max(trial.right().worstBound()).toPlainString())
            .append(',')
            .append(CellFeatures.estimatedChildWork(parent))
            .append('\n');
        work = add(work, trial.work());
        rows++;
      }
      totalLabels = add(totalLabels, work);
      costs.add(
          new ProblemCost(
              definition.id(), definition.split(), candidates.size(), exploration.work(), work));
      progress.accept(
          "Labeled " + definition.id() + ": " + candidates.size() + " complete candidate splits.");
    }
    Map<String, String> digests = new LinkedHashMap<>();
    for (var entry : csv.entrySet()) {
      String content = entry.getValue().toString();
      Files.writeString(output.resolve(entry.getKey() + ".csv"), content, StandardCharsets.UTF_8);
      digests.put(entry.getKey(), EvidenceHashes.text(content));
    }
    Summary summary =
        new Summary(
            "learning-label-evidence-v1",
            protocol.id(),
            EvidenceHashes.of(protocol),
            FoundationDemo.sourceDigest(sourceRoot),
            CellFeatures.VERSION,
            CellFeatures.names(),
            rows,
            digests,
            totalLabels,
            costs);
    Files.writeString(
        output.resolve("summary.json"),
        EvidenceJson.FORMAT.toJson(summary) + "\n",
        StandardCharsets.UTF_8);
    return summary;
  }

  private static CaeWork add(CaeWork first, CaeWork second) {
    return new CaeWork(
        Math.addExact(first.cellAttempts(), second.cellAttempts()),
        Math.addExact(first.mapImages(), second.mapImages()),
        Math.addExact(first.lipschitzBounds(), second.lipschitzBounds()),
        Math.addExact(first.membershipRanges(), second.membershipRanges()),
        Math.addExact(first.membershipLipschitzBounds(), second.membershipLipschitzBounds()));
  }

  record Summary(
      String schemaVersion,
      String protocolId,
      String protocolSha256,
      String sourceSha256,
      String featureVersion,
      List<String> featureNames,
      long rows,
      Map<String, String> dataSha256,
      CaeWork labelWork,
      List<ProblemCost> problems) {
    Summary {
      featureNames = List.copyOf(featureNames);
      dataSha256 = Map.copyOf(dataSha256);
      problems = List.copyOf(problems);
    }
  }

  record ProblemCost(
      String id, String split, int rows, CaeWork explorationWork, CaeWork labelWork) {}
}
