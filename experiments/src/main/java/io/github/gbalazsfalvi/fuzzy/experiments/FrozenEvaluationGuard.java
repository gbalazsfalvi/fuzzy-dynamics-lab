package io.github.gbalazsfalvi.fuzzy.experiments;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Final outcomes require a pre-existing matching freeze and a completely fresh output directory.
 */
final class FrozenEvaluationGuard {
  private FrozenEvaluationGuard() {}

  static void require(Path root, ComparisonProtocol protocol, Path output) throws IOException {
    Path file = root.resolve("research/evaluation-freeze-v1.json");
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("A final evaluation requires a matching pre-run freeze.");
    }
    JsonObject frozen =
        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    if (!"evaluation-freeze-v1".equals(frozen.get("schemaVersion").getAsString())
        || !EvidenceHashes.of(protocol).equals(frozen.get("protocolSha256").getAsString())
        || !FoundationDemo.sourceDigest(root).equals(frozen.get("sourceSha256").getAsString())) {
      throw new IllegalArgumentException(
          "The final protocol or source differs from its pre-run freeze.");
    }
    JsonObject analysis = frozen.getAsJsonObject("analysisSha256");
    if (analysis == null || analysis.size() == 0) {
      throw new IllegalArgumentException(
          "The analysis implementation must be frozen before final outcomes.");
    }
    for (var entry : analysis.entrySet()) {
      String relative = entry.getKey();
      if (!(relative.matches("analysis/[a-zA-Z0-9_-]+[.]py")
          || relative.equals("docs/ANALYSIS_PLAN.md"))) {
        throw new IllegalArgumentException(
            "The analysis freeze contains an unsupported input path.");
      }
      if (!EvidenceHashes.bytes(Files.readAllBytes(root.resolve(relative)))
          .equals(entry.getValue().getAsString())) {
        throw new IllegalArgumentException("Frozen analysis input changed: " + relative);
      }
    }
    requireEmpty(output);
  }

  static void requireEmpty(Path output) throws IOException {
    if (Files.exists(output)) {
      if (!Files.isDirectory(output)) {
        throw new IllegalArgumentException("Final output must be a fresh directory.");
      }
      try (var files = Files.list(output)) {
        if (files.findAny().isPresent()) {
          throw new IllegalArgumentException(
              "Final output already contains complete or partial observations; select a new versioned directory.");
        }
      }
    }
  }
}
