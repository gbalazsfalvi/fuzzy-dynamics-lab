package io.github.gbalazsfalvi.fuzzy.experiments;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Freezes provenance before final outcomes, checks exported inference, and replays saved studies.
 */
public final class EvaluationControl {
  private EvaluationControl() {}

  /**
   * Executes one deterministic provenance or verification action from the project root.
   *
   * @param args exactly one of --parity, --freeze, --verify-validation, or --verify-evaluation
   * @throws IOException if frozen source inputs or artifacts cannot be read or written
   * @throws IllegalArgumentException if syntax, provenance, or saved evidence is inconsistent
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1
        || !List.of("--parity", "--freeze", "--verify-validation", "--verify-evaluation")
            .contains(args[0])) {
      throw new IllegalArgumentException(
          "Usage: evaluationControl --parity | --freeze | --verify-validation | --verify-evaluation");
    }
    Path root = Path.of("").toAbsolutePath();
    if (args[0].equals("--parity")) {
      ModelParity.Summary summary = ModelParity.verify(root.resolve("learning/models"));
      Path output = root.resolve("artifacts/learning/java-parity.json");
      Files.createDirectories(output.getParent());
      Files.writeString(output, EvidenceJson.FORMAT.toJson(summary) + "\n", StandardCharsets.UTF_8);
      System.out.println(
          "Verified "
              + summary.cases()
              + " Python/Java predictions across "
              + summary.models()
              + " frozen models; maximum absolute error="
              + summary.maximumAbsoluteError());
      return;
    }
    if (args[0].equals("--freeze")) {
      freeze(root);
      return;
    }
    boolean finalRun = args[0].equals("--verify-evaluation");
    Path directory = root.resolve(finalRun ? "artifacts/evaluation" : "artifacts/validation");
    ComparisonEvidence.Archive archive = ComparisonReports.read(directory);
    if (!archive.sourceSha256().equals(FoundationDemo.sourceDigest(root))) {
      throw new IllegalArgumentException(
          "Saved numerical evidence belongs to another source state.");
    }
    if (finalRun) {
      JsonObject frozen =
          JsonParser.parseString(
                  Files.readString(
                      root.resolve("research/evaluation-freeze-v1.json"), StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (!archive.protocolSha256().equals(frozen.get("protocolSha256").getAsString())
          || !archive.sourceSha256().equals(frozen.get("sourceSha256").getAsString())) {
        throw new IllegalArgumentException(
            "Final evidence differs from its pre-evaluation freeze.");
      }
    }
    int runs = ComparisonReports.verify(directory, archive, System.out::println);
    if (!finalRun) {
      Path receipt = root.resolve("artifacts/verification/validation-replay.json");
      Files.createDirectories(receipt.getParent());
      Files.writeString(
          receipt,
          EvidenceJson.FORMAT.toJson(
                  java.util.Map.of(
                      "schemaVersion",
                      "validation-replay-v1",
                      "verifiedNumericalRuns",
                      runs,
                      "sourceSha256",
                      archive.sourceSha256(),
                      "protocolSha256",
                      archive.protocolSha256(),
                      "manifestSha256",
                      EvidenceHashes.bytes(Files.readAllBytes(directory.resolve("manifest.json")))))
              + "\n",
          StandardCharsets.UTF_8);
    }
    System.out.println(
        "Verified " + runs + " saved numerical runs, all declared outcomes, and diagnostics.");
  }

  private static void freeze(Path root) throws IOException {
    Path protocolFile = root.resolve("research/protocols/evaluation-locked-v1.json");
    ComparisonProtocol protocol =
        EvidenceJson.FORMAT.fromJson(
            Files.readString(protocolFile, StandardCharsets.UTF_8), ComparisonProtocol.class);
    if (protocol == null
        || !protocol.stage().equals("FROZEN_HELD_OUT")
        || protocol.problems().size() != 120
        || protocol.methods().size() != 32
        || protocol.problems().stream().filter(protocol::timed).count() != 12) {
      throw new IllegalArgumentException(
          "The final freeze requires the complete declared held out cohort.");
    }
    Path selectionFile = root.resolve("research/protocols/evaluation-selection-v1.json");
    JsonObject selected =
        JsonParser.parseString(Files.readString(selectionFile, StandardCharsets.UTF_8))
            .getAsJsonObject();
    JsonObject freeze = selected.deepCopy();
    freeze.addProperty("schemaVersion", "evaluation-freeze-v1");
    freeze.addProperty("sourceSha256", FoundationDemo.sourceDigest(root));
    freeze.addProperty("protocolSha256", EvidenceHashes.of(protocol));
    freeze.addProperty(
        "protocolFileSha256", EvidenceHashes.bytes(Files.readAllBytes(protocolFile)));
    JsonObject analysis = new JsonObject();
    try (var files = Files.walk(root.resolve("analysis"))) {
      for (Path file :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".py"))
              .sorted()
              .toList()) {
        analysis.addProperty(
            root.relativize(file).toString().replace('\\', '/'),
            EvidenceHashes.bytes(Files.readAllBytes(file)));
      }
    }
    Path analysisPlan = root.resolve("docs/ANALYSIS_PLAN.md");
    analysis.addProperty(
        "docs/ANALYSIS_PLAN.md", EvidenceHashes.bytes(Files.readAllBytes(analysisPlan)));
    freeze.add("analysisSha256", analysis);
    for (var method : protocol.methods()) {
      if (method.model() != null) {
        Path file = root.resolve("learning/models/" + method.id() + ".json");
        String actual = EvidenceHashes.bytes(Files.readAllBytes(file));
        if (!actual.equals(
            selected.getAsJsonObject("modelSha256").get(method.id()).getAsString())) {
          throw new IllegalArgumentException(
              "A frozen ranking model changed after validation selection.");
        }
      }
    }
    Path target = root.resolve("research/evaluation-freeze-v1.json");
    if (Files.exists(target)) {
      JsonObject previous =
          JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8))
              .getAsJsonObject();
      freeze.add("frozenAtUtc", previous.get("frozenAtUtc"));
      if (!previous.equals(freeze)) {
        throw new IllegalArgumentException(
            "A completed freeze cannot be silently replaced; create a new version.");
      }
    } else {
      FrozenEvaluationGuard.requireEmpty(root.resolve("artifacts/evaluation"));
      freeze.addProperty("frozenAtUtc", Instant.now().toString());
      Files.writeString(target, EvidenceJson.FORMAT.toJson(freeze) + "\n", StandardCharsets.UTF_8);
    }
    System.out.println(
        "Frozen complete final protocol, numerical source, models, and analysis before evaluation.");
    System.out.println("Numerical source SHA-256: " + freeze.get("sourceSha256").getAsString());
    System.out.println("Protocol SHA-256: " + freeze.get("protocolSha256").getAsString());
  }
}
