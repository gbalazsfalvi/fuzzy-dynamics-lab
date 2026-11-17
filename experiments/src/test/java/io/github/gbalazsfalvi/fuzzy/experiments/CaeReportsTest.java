package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaeReportsTest {
  @TempDir Path directory;

  @Test
  void roundTripsAllExamplesWithoutLosingDecimalEvidenceAndReplaysThem() throws IOException {
    CaeEvidence.Archive archive = CaeDemo.runExamples(sourceRoot());
    Path report = directory.resolve("evidence/cae.json");
    CaeReports.write(archive, report);
    byte[] original = Files.readAllBytes(report);
    CaeEvidence.Archive reloaded = CaeReports.read(report);
    assertEquals(archive, reloaded);
    assertEquals(14, CaeReports.verify(reloaded));
    assertArrayEquals(original, Files.readAllBytes(report));
    JsonObject first = parsed(archive).getAsJsonArray("cases").get(0).getAsJsonObject();
    assertTrue(first.getAsJsonObject("settings").get("tolerance").getAsJsonPrimitive().isString());
    JsonObject cell =
        first
            .getAsJsonObject("outcome")
            .getAsJsonObject("last")
            .getAsJsonArray("cells")
            .get(0)
            .getAsJsonObject();
    assertTrue(
        cell.getAsJsonArray("trajectory")
            .get(0)
            .getAsJsonObject()
            .get("radius")
            .getAsJsonPrimitive()
            .isString());
    assertTrue(
        Files.readString(CaeReports.summaryPath(report)).contains("low-precision-depth-exit"));
    assertTrue(Files.readString(CaeReports.summaryPath(report)).contains("MAX_DEPTH"));
  }

  @Test
  void rejectsEditedGlobalBoundsCellBoundsAndMissingLeaves() throws IOException {
    CaeEvidence.Archive original = fixture();
    for (String mutation : List.of("global", "cell", "missing", "parameter", "radius")) {
      JsonObject json = parsed(original);
      JsonObject evidence = json.getAsJsonArray("cases").get(0).getAsJsonObject();
      JsonObject snapshot = evidence.getAsJsonObject("outcome").getAsJsonObject("last");
      JsonObject cell = snapshot.getAsJsonArray("cells").get(0).getAsJsonObject();
      switch (mutation) {
        case "global" -> snapshot.getAsJsonArray("bounds").set(0, JsonParser.parseString("\"0\""));
        case "cell" -> cell.getAsJsonArray("bounds").set(0, JsonParser.parseString("\"0\""));
        case "missing" -> snapshot.getAsJsonArray("cells").remove(0);
        case "parameter" -> evidence.getAsJsonObject("problem").addProperty("parameter", "1/2");
        case "radius" ->
            cell.getAsJsonArray("trajectory").get(0).getAsJsonObject().addProperty("radius", "0");
        default -> throw new AssertionError("Unrecognized test mutation.");
      }
      assertThrows(
          RuntimeException.class,
          () -> CaeReports.verify(CaeReports.decode(json.toString())),
          mutation);
    }
  }

  @Test
  void rejectsAChangedOperationCountTraceAndStopConditionEvenWithValidSnapshots()
      throws IOException {
    CaeEvidence.Archive original = fixture();
    for (String mutation : List.of("work", "trace", "reason")) {
      JsonObject json = parsed(original);
      JsonObject outcome =
          json.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("outcome");
      switch (mutation) {
        case "work" -> outcome.getAsJsonObject("work").addProperty("cellAttempts", 999);
        case "trace" ->
            outcome
                .getAsJsonArray("history")
                .get(0)
                .getAsJsonObject()
                .addProperty("selection", "UNIFORM");
        case "reason" -> outcome.addProperty("reason", "MAX_CELLS");
        default -> throw new AssertionError("Unrecognized test mutation.");
      }
      assertThrows(
          IllegalArgumentException.class,
          () -> CaeReports.verify(CaeReports.decode(json.toString())),
          mutation);
    }
  }

  @Test
  void rejectsBinaryJsonNumbersUnknownMapsAndUnsupportedSchema() throws IOException {
    CaeEvidence.Archive original = fixture();
    for (String mutation : List.of("number", "map", "schema", "tent-parameter")) {
      JsonObject json = parsed(original);
      JsonObject evidence = json.getAsJsonArray("cases").get(0).getAsJsonObject();
      switch (mutation) {
        case "number" -> evidence.getAsJsonObject("settings").addProperty("tolerance", 0.1);
        case "map" -> evidence.getAsJsonObject("problem").addProperty("map", "DYNAMIC_CLASS_NAME");
        case "schema" -> json.addProperty("schemaVersion", "unknown");
        case "tent-parameter" -> evidence.getAsJsonObject("problem").addProperty("map", "TENT");
        default -> throw new AssertionError("Unrecognized test mutation.");
      }
      assertThrows(RuntimeException.class, () -> CaeReports.decode(json.toString()), mutation);
    }
    assertThrows(RuntimeException.class, () -> CaeReports.decode("null"));
    assertThrows(RuntimeException.class, () -> CaeReports.decode("{"));
    assertThrows(
        RuntimeException.class, () -> CaeReports.decode(CaeReports.encode(original) + " false"));
  }

  @Test
  void validatesArchiveIdentityAndTheExplicitModelRegistry() throws IOException {
    CaeEvidence.Archive original = fixture();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeEvidence.Archive(
                original.schemaVersion(),
                "unreviewed scope",
                original.generatedAtUtc(),
                original.sourceSha256(),
                original.cases()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeEvidence.Archive(
                original.schemaVersion(),
                original.scope(),
                original.generatedAtUtc(),
                "not-a-digest",
                original.cases()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeEvidence.Archive(
                original.schemaVersion(),
                original.scope(),
                original.generatedAtUtc(),
                original.sourceSha256(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CaeEvidence.Archive(
                original.schemaVersion(),
                original.scope(),
                original.generatedAtUtc(),
                original.sourceSha256(),
                List.of(original.cases().getFirst(), original.cases().getFirst())));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CaeEvidence.Problem(CaeEvidence.MapKind.LOGISTIC, null, List.of(), 0));
    CaeEvidence.Problem invalid =
        new CaeEvidence.Problem(
            CaeEvidence.MapKind.LOGISTIC, "5", original.cases().getFirst().problem().peaks(), 0);
    assertThrows(IllegalArgumentException.class, invalid::toCore);
    assertEquals(
        directory.resolve("report-summary.md"),
        CaeReports.summaryPath(directory.resolve("report")));
    assertThrows(IllegalArgumentException.class, () -> CaeDemo.main(new String[] {"--bad"}));
    assertThrows(
        IllegalArgumentException.class, () -> CaeDemo.main(new String[] {"--bad", "file"}));
  }

  private CaeEvidence.Archive fixture() throws IOException {
    CaeProblem problem =
        new CaeProblem(
            new AffineContraction(Rational.of(3, 4)),
            new TentPeak(ExactPoint.of("1/3"), Rational.of(1, 4), Rational.ONE),
            2);
    CaeSettings defaults = CaeSettings.standard(new BigDecimal("0.01"));
    CaeSettings settings =
        new CaeSettings(
            defaults.arithmetic(), defaults.tolerance(), 3, 4, 8, 5, defaults.strategy());
    CaeResult result = new CaeSolver().solve(problem, settings);
    CaeEvidence.Case evidence =
        new CaeEvidence.Case(
            "roundtrip",
            CaeEvidence.Problem.from(problem),
            settings,
            CaeEvidence.Outcome.from(result));
    return new CaeEvidence.Archive(
        CaeEvidence.SCHEMA,
        CaeEvidence.SCOPE,
        "2026-11-17T00:00:00Z",
        FoundationDemo.sourceDigest(sourceRoot()),
        List.of(evidence));
  }

  private Path sourceRoot() throws IOException {
    Path root = directory.resolve("source");
    Files.createDirectories(root.resolve("numerics-core/src/main/java"));
    Files.writeString(
        root.resolve("numerics-core/src/main/java/Example.java"), "class Example {}\n");
    return root;
  }

  private static JsonObject parsed(CaeEvidence.Archive archive) {
    return JsonParser.parseString(CaeReports.encode(archive)).getAsJsonObject();
  }
}
