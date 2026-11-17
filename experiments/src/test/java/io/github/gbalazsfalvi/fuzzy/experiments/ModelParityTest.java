package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModelParityTest {
  @Test
  void exportedRidgeAndFiveNetworksMatchIndependentPythonEstimatorPredictions() throws Exception {
    Path root = Path.of("..").toAbsolutePath().normalize();
    ModelParity.Summary summary = ModelParity.verify(root.resolve("learning/models"));
    assertEquals(6, summary.models());
    assertEquals(576, summary.cases());
    assertTrue(summary.maximumAbsoluteError() < 1e-10);
  }
}
