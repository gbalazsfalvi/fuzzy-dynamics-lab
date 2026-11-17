package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.ranking.DenseRankingModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cross-runtime inference parity against frozen predictions from the actual fitted Python
 * estimators.
 */
final class ModelParity {
  private ModelParity() {}

  static Summary verify(Path directory) throws IOException {
    Fixture fixture =
        EvidenceJson.FORMAT.fromJson(
            Files.readString(directory.resolve("parity-cases.json"), StandardCharsets.UTF_8),
            Fixture.class);
    if (fixture == null
        || !"dense-ranker-parity-v1".equals(fixture.schemaVersion())
        || fixture.absoluteTolerance() <= 0
        || fixture.absoluteTolerance() > 1e-8
        || fixture.relativeTolerance() < 0
        || fixture.relativeTolerance() > 1e-8
        || fixture.models().isEmpty()) {
      throw new IllegalArgumentException("The model parity fixture is invalid.");
    }
    int cases = 0;
    double maximumError = 0;
    for (ModelCases modelCases : fixture.models()) {
      if (!modelCases.file().matches("[a-z0-9-]+[.]json") || modelCases.cases().isEmpty()) {
        throw new IllegalArgumentException("Model parity cases require safe artifact identities.");
      }
      String json = Files.readString(directory.resolve(modelCases.file()), StandardCharsets.UTF_8);
      if (!EvidenceHashes.text(json).equals(modelCases.sha256())) {
        throw new IllegalArgumentException(
            "Frozen model file hash differs from its parity fixture.");
      }
      DenseRankingModel model = EvidenceJson.FORMAT.fromJson(json, DenseRankingModel.class);
      if (model == null || !model.id().equals(modelCases.id())) {
        throw new IllegalArgumentException(
            "Frozen model identity differs from its parity fixture.");
      }
      for (Prediction expected : modelCases.cases()) {
        if (!Double.isFinite(expected.pythonPrediction())) {
          throw new IllegalArgumentException("Python parity predictions must be finite.");
        }
        double prediction = model.predict(expected.features());
        double error = Math.abs(prediction - expected.pythonPrediction());
        double tolerance =
            fixture.absoluteTolerance()
                + fixture.relativeTolerance() * Math.abs(expected.pythonPrediction());
        if (error > tolerance) {
          throw new IllegalArgumentException(
              "Python and Java inference disagree for " + model.id());
        }
        maximumError = Math.max(maximumError, error);
        cases++;
      }
    }
    return new Summary(fixture.models().size(), cases, maximumError);
  }

  record Fixture(
      String schemaVersion,
      double absoluteTolerance,
      double relativeTolerance,
      List<ModelCases> models) {}

  record ModelCases(String id, String file, String sha256, List<Prediction> cases) {}

  record Prediction(List<Double> features, double pythonPrediction) {}

  record Summary(int models, int cases, double maximumAbsoluteError) {}
}
