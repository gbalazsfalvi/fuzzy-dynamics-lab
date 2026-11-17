package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrozenEvaluationGuardTest {
  @TempDir Path directory;

  @Test
  void admitsOnlyMatchingInputsIntoANewEmptyObservationDirectory() throws IOException {
    ComparisonProtocol protocol = inputs();
    Path output = directory.resolve("new-output");
    FrozenEvaluationGuard.require(directory, protocol, output);
    Files.createDirectories(output);
    FrozenEvaluationGuard.require(directory, protocol, output);
    Files.createDirectories(output.resolve("runs"));
    Files.writeString(output.resolve("runs/partial.json.gz"), "a partial observation");
    assertThrows(
        IllegalArgumentException.class,
        () -> FrozenEvaluationGuard.require(directory, protocol, output));
    Path file = directory.resolve("not-directory");
    Files.writeString(file, "x");
    assertThrows(IllegalArgumentException.class, () -> FrozenEvaluationGuard.requireEmpty(file));
  }

  @Test
  void rejectsChangedProtocolNumericalSourcesAndAnalysisEvenBeforeTheFirstSolve()
      throws IOException {
    ComparisonProtocol protocol = inputs();
    Path output = directory.resolve("new-output");
    JsonObject altered =
        JsonParser.parseString(EvidenceJson.FORMAT.toJson(protocol)).getAsJsonObject();
    altered.addProperty("maxRefinements", 3);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            FrozenEvaluationGuard.require(
                directory,
                EvidenceJson.FORMAT.fromJson(altered, ComparisonProtocol.class),
                output));
    Path source = directory.resolve("numerics-core/src/main/java/Example.java");
    Files.writeString(source, "class Example { int changed; }\n");
    assertThrows(
        IllegalArgumentException.class,
        () -> FrozenEvaluationGuard.require(directory, protocol, output));
    Files.writeString(source, "class Example {}\n");
    Files.writeString(directory.resolve("analysis/evaluate.py"), "changed");
    assertThrows(
        IllegalArgumentException.class,
        () -> FrozenEvaluationGuard.require(directory, protocol, output));
  }

  @Test
  void rejectsMissingOrUnsafeAnalysisInputsInTheFreeze() throws IOException {
    ComparisonProtocol protocol = inputs();
    Path file = directory.resolve("research/evaluation-freeze-v1.json");
    JsonObject freeze = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    freeze.remove("analysisSha256");
    Files.writeString(file, freeze.toString());
    assertThrows(
        IllegalArgumentException.class,
        () -> FrozenEvaluationGuard.require(directory, protocol, directory.resolve("out")));
    JsonObject unsafe = new JsonObject();
    unsafe.addProperty("../outside.py", "0".repeat(64));
    freeze.add("analysisSha256", unsafe);
    Files.writeString(file, freeze.toString());
    assertThrows(
        IllegalArgumentException.class,
        () -> FrozenEvaluationGuard.require(directory, protocol, directory.resolve("out")));
  }

  private ComparisonProtocol inputs() throws IOException {
    Files.createDirectories(directory.resolve("numerics-core/src/main/java"));
    Files.writeString(
        directory.resolve("numerics-core/src/main/java/Example.java"), "class Example {}\n");
    Files.createDirectories(directory.resolve("analysis"));
    Path analysis = directory.resolve("analysis/evaluate.py");
    Files.writeString(analysis, "print('fixture')\n");
    JsonObject input =
        JsonParser.parseString(EvidenceJson.FORMAT.toJson(ComparisonTest.protocol()))
            .getAsJsonObject();
    input.addProperty("stage", "FROZEN_HELD_OUT");
    ComparisonProtocol protocol = EvidenceJson.FORMAT.fromJson(input, ComparisonProtocol.class);
    JsonObject frozen = new JsonObject();
    frozen.addProperty("schemaVersion", "evaluation-freeze-v1");
    frozen.addProperty("sourceSha256", FoundationDemo.sourceDigest(directory));
    frozen.addProperty("protocolSha256", EvidenceHashes.of(protocol));
    JsonObject hashes = new JsonObject();
    hashes.addProperty("analysis/evaluate.py", EvidenceHashes.bytes(Files.readAllBytes(analysis)));
    frozen.add("analysisSha256", hashes);
    Files.createDirectories(directory.resolve("research"));
    Files.writeString(directory.resolve("research/evaluation-freeze-v1.json"), frozen.toString());
    return protocol;
  }
}
