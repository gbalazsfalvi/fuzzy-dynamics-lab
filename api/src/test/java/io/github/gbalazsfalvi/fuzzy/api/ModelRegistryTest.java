package io.github.gbalazsfalvi.fuzzy.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import io.github.gbalazsfalvi.fuzzy.ranking.CellFeatures;
import io.github.gbalazsfalvi.fuzzy.ranking.DenseRankingModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelRegistryTest {
  @TempDir Path directory;

  @Test
  void onlyValidatedModelIdsReachRankedSolver() throws Exception {
    int features = CellFeatures.names().size();
    var model =
        new DenseRankingModel(
            "ridge",
            CellFeatures.VERSION,
            CellFeatures.names(),
            Collections.nCopies(features, 0.0),
            Collections.nCopies(features, 1.0),
            0.0,
            1.0,
            List.of(
                new DenseRankingModel.Layer(
                    Collections.nCopies(features, List.of(0.0)),
                    List.of(0.0),
                    DenseRankingModel.Activation.IDENTITY)),
            "a".repeat(64),
            "b".repeat(64),
            11);
    Files.writeString(directory.resolve("ridge.json"), new Gson().toJson(model));
    Files.writeString(directory.resolve("invalid.json"), "{}");
    ModelRegistry registry = new ModelRegistry(directory);
    assertEquals(1, registry.descriptions().size());
    assertEquals(model, registry.require("ridge"));
    assertThrows(IllegalArgumentException.class, () -> registry.require("../ridge.json"));
    try (JobService jobs = new JobService(registry)) {
      var request =
          JobRequest.parse(
              JobRequestTest.VALID
                  .replace("MAXIMUM_BOUND", "RANKED")
                  .replace("\"maxDepth\":8", "\"maxDepth\":8,\"modelId\":\"ridge\""));
      var id = jobs.submit(request);
      long deadline = System.nanoTime() + 10_000_000_000L;
      while (!jobs.terminal(id) && System.nanoTime() < deadline) Thread.sleep(10);
      assertEquals("READY", jobs.get(id).get("status"));
      assertNotNull(jobs.get(id).get("result"));
    }
  }
}
