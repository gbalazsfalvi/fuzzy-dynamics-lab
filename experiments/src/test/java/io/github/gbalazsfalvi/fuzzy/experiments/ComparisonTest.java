package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparisonTest {
  @TempDir Path directory;

  @Test
  void observationsOutsideTheDeclaredTimingCohortRunOnceWithoutPretendingToBeRepeatedTimings()
      throws IOException {
    JsonObject json =
        JsonParser.parseString(EvidenceJson.FORMAT.toJson(protocol())).getAsJsonObject();
    json.addProperty("stage", "VALIDATION_ONLY");
    json.getAsJsonArray("problems")
        .forEach(value -> value.getAsJsonObject().addProperty("timingCohort", false));
    ComparisonProtocol scoped = EvidenceJson.FORMAT.fromJson(json, ComparisonProtocol.class);
    Path output = directory.resolve("single-observation");
    ComparisonEvidence.Archive archive =
        ComparisonRunner.run(sourceRoot(), scoped, output, ignored -> {});
    assertEquals(6, archive.runs().stream().mapToInt(row -> row.timings().size()).sum());
    assertEquals(6, ComparisonReports.verify(output, archive, ignored -> {}));
    assertTrue(
        archive.runs().stream()
            .filter(row -> row.evidenceFile() != null)
            .allMatch(row -> row.evidenceFile().endsWith(".json.gz")));
  }

  @Test
  void finalRunsCannotStartWithoutMatchingFrozenSourceAndAnalysisProvenance() throws IOException {
    JsonObject json =
        JsonParser.parseString(EvidenceJson.FORMAT.toJson(protocol())).getAsJsonObject();
    json.addProperty("stage", "FROZEN_HELD_OUT");
    ComparisonProtocol frozen = EvidenceJson.FORMAT.fromJson(json, ComparisonProtocol.class);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ComparisonRunner.run(
                sourceRoot(), frozen, directory.resolve("unfrozen-final"), ignored -> {}));
  }

  @Test
  void sourceChangesDuringARunCannotAcquireTheFinalSourcesProvenanceHash() throws IOException {
    Path source = sourceRoot();
    assertThrows(
        IllegalStateException.class,
        () ->
            ComparisonRunner.run(
                source,
                protocol(),
                directory.resolve("changing-source"),
                ignored -> {
                  try {
                    Files.writeString(
                        source.resolve("numerics-core/src/main/java/Example.java"),
                        "class Example { int changed; }\n");
                  } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                  }
                }));
  }

  @Test
  void frozenRankedMethodsMustBindTheActualModelWeightsToTheConfiguration() throws IOException {
    var model =
        EvidenceJson.FORMAT.fromJson(
            Files.readString(Path.of("../learning/models/ridge.json")),
            io.github.gbalazsfalvi.fuzzy.ranking.DenseRankingModel.class);
    var method =
        new ComparisonProtocol.Method(
            "frozen-ridge", ComparisonProtocol.Kind.CAE, CaeSettings.Strategy.RANKED, model);
    CaeProblem problem =
        new CaeProblem(
            new AffineContraction(Rational.of(3, 4)),
            new TentPeak(ExactPoint.of("1/2"), Rational.of(1, 4), Rational.ONE),
            2);
    var payload = ComparisonEngine.solve(protocol(), method, problem, new BigDecimal("0.2"));
    assertNotNull(payload.cae());
    assertTrue(
        ComparisonEngine.verify(protocol(), method, problem, new BigDecimal("0.2"), payload)
                .intervalWork()
                .cellAttempts()
            > 0);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ComparisonProtocol.Method(
                "missing-model", ComparisonProtocol.Kind.CAE, CaeSettings.Strategy.RANKED));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ComparisonProtocol.Method(
                "extra-model", ComparisonProtocol.Kind.CAE, CaeSettings.Strategy.UNIFORM, model));
  }

  @Test
  void recordsEveryPairedOutcomeAndReplaysTypedGeometryWithoutChangingObservations()
      throws IOException {
    ComparisonProtocol protocol = protocol();
    Path output = directory.resolve("comparison");
    ComparisonEvidence.Archive archive =
        ComparisonRunner.run(sourceRoot(), protocol, output, ignored -> {});
    ComparisonReports.write(output, archive);
    assertEquals(9, archive.runs().size());
    assertEquals(
        3, archive.runs().stream().filter(row -> row.status().equals("UNAVAILABLE")).count());
    assertEquals(30, archive.runs().stream().mapToInt(row -> row.timings().size()).sum());
    assertTrue(
        archive.runs().stream()
            .flatMap(row -> row.diagnostics().cuts().stream())
            .anyMatch(
                cut -> cut.status().equals("APPROXIMATION_EMPTY") && cut.hausdorff() == null));
    ComparisonEvidence.Archive loaded = ComparisonReports.read(output);
    assertEquals(archive, loaded);
    Map<String, String> before = hashes(output);
    assertEquals(6, ComparisonReports.verify(output, loaded, ignored -> {}));
    assertEquals(before, hashes(output));
    for (String name :
        List.of(
            "outcomes.csv",
            "measurements.csv",
            "alpha-diagnostics.csv",
            "reference-errors.csv",
            "refinement-curves.csv",
            "summary.md")) {
      assertTrue(Files.size(output.resolve(name)) > 0);
    }
    assertTrue(Files.readString(output.resolve("summary.md")).contains("PSO preprocessing"));
  }

  @Test
  void rejectsMissingRowsChangedMetricsAndEditedMethodOrder() throws IOException {
    Path output = directory.resolve("comparison");
    ComparisonEvidence.Archive archive =
        ComparisonRunner.run(sourceRoot(), protocol(), output, ignored -> {});
    for (String mutation : List.of("missing", "metric", "order", "configuration", "path")) {
      JsonObject json =
          JsonParser.parseString(EvidenceJson.FORMAT.toJson(archive)).getAsJsonObject();
      JsonObject first = json.getAsJsonArray("runs").get(0).getAsJsonObject();
      switch (mutation) {
        case "missing" -> json.getAsJsonArray("runs").remove(0);
        case "metric" -> first.addProperty("maximumBound", "0");
        case "order" -> {
          JsonObject timing = first.getAsJsonArray("timings").get(0).getAsJsonObject();
          timing.addProperty("orderIndex", (timing.get("orderIndex").getAsInt() + 1) % 3);
        }
        case "configuration" -> first.addProperty("configurationSha256", "0".repeat(64));
        case "path" -> first.addProperty("evidenceFile", "../outside.json");
        default -> throw new AssertionError("Unknown mutation.");
      }
      ComparisonEvidence.Archive modified =
          EvidenceJson.FORMAT.fromJson(json, ComparisonEvidence.Archive.class);
      assertThrows(
          IllegalArgumentException.class,
          () -> ComparisonReports.verify(output, modified, ignored -> {}),
          mutation);
    }
  }

  @Test
  void rejectsTamperedNumericsEvenWhenTheirFileHashHasAlsoBeenUpdated() throws IOException {
    Path output = directory.resolve("comparison");
    ComparisonEvidence.Archive archive =
        ComparisonRunner.run(sourceRoot(), protocol(), output, ignored -> {});
    ComparisonEvidence.Run first = archive.runs().getFirst();
    Path numericalFile = output.resolve(first.evidenceFile());
    JsonObject payload = JsonParser.parseString(Files.readString(numericalFile)).getAsJsonObject();
    payload
        .getAsJsonObject("cae")
        .getAsJsonObject("last")
        .getAsJsonArray("bounds")
        .set(0, JsonParser.parseString("\"0\""));
    String modified = payload.toString();
    Files.writeString(numericalFile, modified);
    assertThrows(
        IllegalArgumentException.class, () -> ComparisonReports.readPayload(output, first));
    JsonObject manifest =
        JsonParser.parseString(EvidenceJson.FORMAT.toJson(archive)).getAsJsonObject();
    manifest
        .getAsJsonArray("runs")
        .get(0)
        .getAsJsonObject()
        .addProperty("evidenceSha256", EvidenceHashes.text(modified));
    ComparisonEvidence.Archive resigned =
        EvidenceJson.FORMAT.fromJson(manifest, ComparisonEvidence.Archive.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> ComparisonReports.verify(output, resigned, ignored -> {}));
  }

  @Test
  void validatesExactEvidenceTokensProtocolIdentityAndCliArguments() {
    assertThrows(RuntimeException.class, () -> EvidenceJson.FORMAT.fromJson("0.5", Rational.class));
    assertEquals(Rational.of(1, 2), EvidenceJson.FORMAT.fromJson("\"0.5\"", Rational.class));
    assertEquals("\"1/2\"", EvidenceJson.FORMAT.toJson(Rational.of(1, 2)));
    assertThrows(IllegalArgumentException.class, () -> ComparisonDemo.main(new String[] {"--bad"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> ComparisonDemo.main(new String[] {"--bad", "a", "--output", "b"}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ComparisonProtocol.Method(
                "unsafe/path", ComparisonProtocol.Kind.CAE, CaeSettings.Strategy.UNIFORM));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ComparisonProtocol.Method(
                "wrong", ComparisonProtocol.Kind.ALPHA_GRID, CaeSettings.Strategy.UNIFORM));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ComparisonProtocol.Method("missing", ComparisonProtocol.Kind.CAE, null));
    assertThrows(
        IllegalArgumentException.class, () -> new ComparisonEvidence.Timing(0, 0, 10, 20, 29));
    assertThrows(
        IllegalArgumentException.class, () -> new ComparisonEvidence.Timing(-1, 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class, () -> new ComparisonEngine.Payload(null, null, null, null));
  }

  static ComparisonProtocol protocol() {
    TentPeak scalar = new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 5), Rational.ONE);
    TentPeak weak = new TentPeak(ExactPoint.of("1/5"), Rational.of(1, 50), Rational.of(1, 100));
    TentPeak square = new TentPeak(ExactPoint.of("1/4", "3/4"), Rational.of(1, 4), Rational.ONE);
    return new ComparisonProtocol(
        "test-pilot",
        "2026-11-17",
        "EXPLORATORY_PILOT",
        8,
        List.of(new BigDecimal("0.2")),
        2,
        3,
        4,
        5,
        16,
        1,
        5,
        17112026L,
        4,
        List.of(Rational.of(1, 200), Rational.ONE),
        List.of(
            new ComparisonProtocol.Method(
                "uniform", ComparisonProtocol.Kind.CAE, CaeSettings.Strategy.UNIFORM),
            new ComparisonProtocol.Method("alpha", ComparisonProtocol.Kind.ALPHA_GRID, null),
            new ComparisonProtocol.Method(
                "published", ComparisonProtocol.Kind.PUBLISHED_PWL_CORE, null)),
        List.of(
            definition(
                "affine",
                new CaeProblem(new AffineContraction(Rational.of(3, 4)), scalar, 2),
                true),
            definition(
                "logistic-weak",
                new CaeProblem(new LogisticMap(Rational.of(37, 10)), weak, 2),
                true),
            definition(
                "coupled",
                new CaeProblem(new CoupledTentMap(Rational.of(1, 4)), square, 2),
                false)));
  }

  private static ComparisonProtocol.Problem definition(
      String id, CaeProblem problem, boolean qualified) {
    return new ComparisonProtocol.Problem(id, id, qualified, CaeEvidence.Problem.from(problem));
  }

  private Path sourceRoot() throws IOException {
    Path source = directory.resolve("source/numerics-core/src/main/java");
    Files.createDirectories(source);
    Files.writeString(source.resolve("Example.java"), "class Example {}\n");
    return directory.resolve("source");
  }

  private static Map<String, String> hashes(Path directory) throws IOException {
    Map<String, String> hashes = new java.util.TreeMap<>();
    try (var paths = Files.walk(directory)) {
      for (Path file : paths.filter(Files::isRegularFile).toList()) {
        hashes.put(
            directory.relativize(file).toString(), EvidenceHashes.text(Files.readString(file)));
      }
    }
    return hashes;
  }
}
