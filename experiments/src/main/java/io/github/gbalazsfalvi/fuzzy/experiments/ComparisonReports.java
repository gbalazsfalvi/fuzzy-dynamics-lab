package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.RefinementEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Manifest, raw observations, typed geometry files, and strict saved evidence replay. */
final class ComparisonReports {
  private ComparisonReports() {}

  static String writePayload(Path directory, String relative, ComparisonEngine.Payload payload)
      throws IOException {
    Path target = payloadPath(directory, relative);
    Files.createDirectories(target.getParent());
    String json = EvidenceJson.FORMAT.toJson(payload) + "\n";
    if (relative.endsWith(".gz")) {
      try (var compressed = new GZIPOutputStream(Files.newOutputStream(target))) {
        compressed.write(json.getBytes(StandardCharsets.UTF_8));
      }
    } else {
      Files.writeString(target, json, StandardCharsets.UTF_8);
    }
    return EvidenceHashes.bytes(Files.readAllBytes(target));
  }

  static ComparisonEngine.Payload readPayload(Path directory, ComparisonEvidence.Run row)
      throws IOException {
    return readPayload(directory, row.evidenceFile(), row.evidenceSha256());
  }

  static ComparisonEngine.Payload readPayload(Path directory, String relative, String digest)
      throws IOException {
    Path file = payloadPath(directory, relative);
    if (Files.size(file) > 64L * 1024 * 1024) {
      throw new IllegalArgumentException("Numerical artifact exceeds the supported file size.");
    }
    byte[] stored = Files.readAllBytes(file);
    if (!EvidenceHashes.bytes(stored).equals(digest)) {
      throw new IllegalArgumentException("Numerical evidence digest mismatch: " + relative);
    }
    byte[] content = stored;
    if (relative.endsWith(".gz")) {
      try (var compressed = new GZIPInputStream(new ByteArrayInputStream(stored))) {
        content = compressed.readNBytes(64 * 1024 * 1024 + 1);
      }
    }
    if (content.length > 64 * 1024 * 1024) {
      throw new IllegalArgumentException(
          "Decompressed numerical artifact exceeds the supported size.");
    }
    String json = new String(content, StandardCharsets.UTF_8);
    ComparisonEngine.Payload payload =
        EvidenceJson.FORMAT.fromJson(json, ComparisonEngine.Payload.class);
    if (payload == null) {
      throw new IllegalArgumentException("Numerical evidence cannot be null.");
    }
    return payload;
  }

  static ComparisonEvidence.Archive read(Path directory) throws IOException {
    ComparisonEvidence.Archive archive =
        EvidenceJson.FORMAT.fromJson(
            Files.readString(directory.resolve("manifest.json"), StandardCharsets.UTF_8),
            ComparisonEvidence.Archive.class);
    if (archive == null) {
      throw new IllegalArgumentException("A comparison manifest cannot be null.");
    }
    return archive;
  }

  static void write(Path directory, ComparisonEvidence.Archive archive) throws IOException {
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve("manifest.json"),
        EvidenceJson.FORMAT.toJson(archive) + "\n",
        StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("summary.md"), summary(archive), StandardCharsets.UTF_8);
    writeCsv(directory, archive);
  }

  static int verify(Path directory, ComparisonEvidence.Archive archive, Consumer<String> progress)
      throws IOException {
    ComparisonProtocol protocol = archive.protocol();
    Map<String, ComparisonEvidence.Run> rows = new LinkedHashMap<>();
    for (ComparisonEvidence.Run row : archive.runs()) {
      rows.put(row.id(), row);
    }
    int expectedCount =
        Math.multiplyExact(
            protocol.problems().size(),
            Math.multiplyExact(protocol.methods().size(), protocol.tolerances().size()));
    if (rows.size() != expectedCount) {
      throw new IllegalArgumentException("The manifest does not cover the complete paired cohort.");
    }
    Map<String, List<Integer>> expectedOrder = expectedOrder(protocol);
    int checked = 0;
    for (ComparisonProtocol.Problem definition : protocol.problems()) {
      CaeProblem problem = definition.definition().toCore();
      ComparisonDiagnostics.References references =
          ComparisonDiagnostics.prepare(protocol, definition);
      for (BigDecimal tolerance : protocol.tolerances()) {
        for (ComparisonProtocol.Method method : protocol.methods()) {
          String id = ComparisonRunner.runId(definition, method, tolerance);
          ComparisonEvidence.Run row = rows.get(id);
          if (row == null
              || !row.problemId().equals(definition.id())
              || !row.methodId().equals(method.id())
              || row.tolerance().compareTo(tolerance) != 0
              || !row.configurationSha256()
                  .equals(
                      EvidenceHashes.of(
                          new ComparisonEvidence.Configuration(
                              archive.protocolSha256(), definition, method, tolerance)))) {
            throw new IllegalArgumentException(
                "A row is missing or belongs to another configuration: " + id);
          }
          String excluded = ComparisonEngine.exclusion(method, problem);
          if (excluded != null) {
            ComparisonEvidence.Run expected =
                new ComparisonEvidence.Run(
                    id,
                    definition.id(),
                    method.id(),
                    tolerance,
                    row.configurationSha256(),
                    "UNAVAILABLE",
                    excluded,
                    null,
                    0,
                    ComparisonEngine.NO_INTERVAL_WORK,
                    0,
                    new ComparisonEngine.Verification(ComparisonEngine.NO_INTERVAL_WORK, 0, 0),
                    List.of(),
                    new ComparisonDiagnostics.Data("METHOD_UNAVAILABLE", List.of(), List.of()),
                    null,
                    null);
            if (!expected.equals(row)) {
              throw new IllegalArgumentException(
                  "An unavailable method contains fabricated evidence.");
            }
            continue;
          }
          if (row.timings().size() != protocol.repetitions(definition)) {
            throw new IllegalArgumentException("A timed row is missing a declared repetition.");
          }
          for (int repeat = 0; repeat < row.timings().size(); repeat++) {
            ComparisonEvidence.Timing timing = row.timings().get(repeat);
            if (timing.repetition() != repeat
                || timing.orderIndex() != expectedOrder.get(id).get(repeat)) {
              throw new IllegalArgumentException(
                  "The saved method order differs from the protocol seed.");
            }
          }
          ComparisonEngine.Payload stored = readPayload(directory, row);
          ComparisonEngine.Payload replayed =
              ComparisonEngine.solve(protocol, method, problem, tolerance);
          if (!stored.equals(replayed)) {
            throw new IllegalArgumentException(
                "Saved numerical output differs from deterministic replay: " + id);
          }
          ComparisonEngine.Verification verification =
              ComparisonEngine.verify(protocol, method, problem, tolerance, stored);
          ComparisonEvidence.Run expected =
              new ComparisonEvidence.Run(
                  id,
                  definition.id(),
                  method.id(),
                  tolerance,
                  row.configurationSha256(),
                  stored.status(),
                  stored.reason(),
                  stored.bound(),
                  stored.geometrySize(),
                  stored.intervalWork(),
                  stored.exactPointEvaluations(),
                  verification,
                  row.timings(),
                  ComparisonDiagnostics.measure(stored, references),
                  row.evidenceFile(),
                  row.evidenceSha256());
          if (!expected.equals(row)) {
            throw new IllegalArgumentException(
                "Saved metrics or diagnostics differ from recomputation: " + id);
          }
          checked++;
        }
      }
      progress.accept("Verified comparison rows for " + definition.id());
    }
    return checked;
  }

  private static Map<String, List<Integer>> expectedOrder(ComparisonProtocol protocol) {
    Map<String, List<Integer>> order = new LinkedHashMap<>();
    Random random = new Random(protocol.orderSeed());
    for (ComparisonProtocol.Problem problem : protocol.problems()) {
      for (BigDecimal tolerance : protocol.tolerances()) {
        for (int repetition = -protocol.warmups(problem);
            repetition < protocol.repetitions(problem);
            repetition++) {
          List<ComparisonProtocol.Method> methods = new ArrayList<>(protocol.methods());
          Collections.shuffle(methods, random);
          if (repetition >= 0) {
            for (int index = 0; index < methods.size(); index++) {
              order
                  .computeIfAbsent(
                      ComparisonRunner.runId(problem, methods.get(index), tolerance),
                      ignored -> new ArrayList<>())
                  .add(index);
            }
          }
        }
      }
    }
    return order;
  }

  private static Path payloadPath(Path directory, String relative) {
    if (relative == null || !relative.matches("runs/[a-z0-9-]+[.]json(?:[.]gz)?")) {
      throw new IllegalArgumentException(
          "A numerical artifact must have a safe relative run filename.");
    }
    Path base = directory.toAbsolutePath().normalize();
    Path target = base.resolve(relative).normalize();
    if (!target.startsWith(base)) {
      throw new IllegalArgumentException(
          "Numerical artifact path escapes the comparison directory.");
    }
    return target;
  }

  private static String summary(ComparisonEvidence.Archive archive) {
    StringBuilder text =
        new StringBuilder("# Fuzzy Dynamics Lab comparison\n\n")
            .append("Stage: ")
            .append(archive.protocol().stage())
            .append("\n\n")
            .append("Protocol: ")
            .append(archive.protocol().id())
            .append("\n\n")
            .append("Generated: ")
            .append(archive.generatedAtUtc())
            .append("\n\n")
            .append("Source SHA-256: ")
            .append(archive.sourceSha256())
            .append("\n\n")
            .append("Protocol SHA-256: ")
            .append(archive.protocolSha256())
            .append("\n\n")
            .append(
                "| Method | Eligible | Certified | Budget exits | Other failures | Unavailable | Median observed total ms |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    for (ComparisonProtocol.Method method : archive.protocol().methods()) {
      List<ComparisonEvidence.Run> rows =
          archive.runs().stream().filter(row -> row.methodId().equals(method.id())).toList();
      long unavailable = rows.stream().filter(row -> row.status().equals("UNAVAILABLE")).count();
      long certified = rows.stream().filter(row -> row.status().equals("CERTIFIED")).count();
      long budget = rows.stream().filter(row -> row.status().equals("BUDGET_EXHAUSTED")).count();
      List<Long> medians =
          rows.stream()
              .filter(row -> !row.timings().isEmpty())
              .filter(
                  row ->
                      archive.protocol().problems().stream()
                          .anyMatch(
                              problem ->
                                  problem.id().equals(row.problemId())
                                      && archive.protocol().timed(problem)))
              .map(ComparisonEvidence.Run::medianTotalNanoseconds)
              .sorted()
              .toList();
      String median =
          medians.isEmpty()
              ? "n/a"
              : BigDecimal.valueOf(medians.get(medians.size() / 2), 6).toPlainString();
      text.append("| ")
          .append(method.id())
          .append(" | ")
          .append(rows.size() - unavailable)
          .append(" | ")
          .append(certified)
          .append(" | ")
          .append(budget)
          .append(" | ")
          .append(rows.size() - unavailable - certified - budget)
          .append(" | ")
          .append(unavailable)
          .append(" | ")
          .append(median)
          .append(" |\n");
    }
    return text.append("\nThese descriptive times include solve and post-solve verification, with ")
        .append(archive.protocol().warmupRepetitions())
        .append(" warmup repetition and ")
        .append(archive.protocol().timingRepetitions())
        .append(" timed repetitions for each eligible row in the declared timing cohort. ")
        .append(
            "Rows outside that cohort have one unwarmed numerical observation and are excluded from timing statistics. ")
        .append(
            "Reference preparation, diagnostics, report IO, and cold JVM startup are excluded. ")
        .append(
            "Each method's observed median includes its resource exits. No unconditional speedup is inferred.\n\n")
        .append(
            "The four CAE policies share the same arithmetic, bounds, tolerance, budgets, and fairness. ")
        .append("Exact scalar alpha cuts and exact PWL transport use different representations; ")
        .append("their exact point counts are recorded separately from decimal interval work.\n\n")
        .append(
            "The published baseline is the exact PWL transport specialization of Kupka and Skorupova (2021), ")
        .append(
            "Section 4.1. It excludes PSO preprocessing and is eligible only for exact affine and tent maps. ")
        .append("It is not a reproduction of the authors' nonlinear timings.\n\n")
        .append(
            "Qualified continuous CAE discrepancy brackets use an independent exact cut staircase ")
        .append(
            "and its explicit vertical error. Sparse diagnostic levels preserve empty cut mismatches. ")
        .append("Missing joint two dimensional references are explicitly marked unavailable.\n\n")
        .append("Files: manifest.json, outcomes.csv, measurements.csv, alpha-diagnostics.csv, ")
        .append(
            "reference-errors.csv, refinement-curves.csv, and individually hashed runs/*.json.\n\n")
        .append(
            "The declared stage distinguishes pilot, validation, and held out runs. No stage constitutes independent proof review ")
        .append(
            "or a scientific novelty claim. All outcomes, including unavailable methods and budget exits, remain in the report.\n")
        .toString();
  }

  private static void writeCsv(Path directory, ComparisonEvidence.Archive archive)
      throws IOException {
    StringBuilder outcomes = new StringBuilder();
    StringBuilder timings = new StringBuilder();
    StringBuilder cuts = new StringBuilder();
    StringBuilder errors = new StringBuilder();
    StringBuilder curves = new StringBuilder();
    csv(
        outcomes,
        "run",
        "problem",
        "method",
        "tolerance",
        "status",
        "reason",
        "maximumBound",
        "geometryElements",
        "medianTotalNanoseconds",
        "algorithmMapImages",
        "algorithmLipschitzBounds",
        "algorithmMembershipRanges",
        "algorithmExactPointEvaluations",
        "verificationMapImages",
        "verificationExactPointEvaluations",
        "configurationSha256");
    csv(
        timings,
        "run",
        "repetition",
        "orderIndex",
        "solveNanoseconds",
        "verificationNanoseconds",
        "totalNanoseconds");
    csv(
        cuts,
        "run",
        "time",
        "alpha",
        "status",
        "hausdorff",
        "referenceIntervals",
        "approximationElements");
    csv(
        errors,
        "run",
        "time",
        "quality",
        "distanceToStaircase",
        "referenceError",
        "lower",
        "upper",
        "certificate");
    csv(curves, "run", "refinement", "currentBound", "bestBound", "activeCells", "selection");
    for (ComparisonEvidence.Run row : archive.runs()) {
      csv(
          outcomes,
          row.id(),
          row.problemId(),
          row.methodId(),
          row.tolerance(),
          row.status(),
          row.reason(),
          row.maximumBound(),
          row.geometrySize(),
          row.timings().isEmpty() ? null : row.medianTotalNanoseconds(),
          row.algorithmIntervalWork().mapImages(),
          row.algorithmIntervalWork().lipschitzBounds(),
          row.algorithmIntervalWork().membershipRanges(),
          row.algorithmExactPointEvaluations(),
          row.verificationWork().intervalWork().mapImages(),
          row.verificationWork().exactPointEvaluations(),
          row.configurationSha256());
      for (ComparisonEvidence.Timing timing : row.timings()) {
        csv(
            timings,
            row.id(),
            timing.repetition(),
            timing.orderIndex(),
            timing.solveNanoseconds(),
            timing.verificationNanoseconds(),
            timing.totalNanoseconds());
      }
      for (ComparisonDiagnostics.CutDiagnostic cut : row.diagnostics().cuts()) {
        csv(
            cuts,
            row.id(),
            cut.time(),
            cut.alpha(),
            cut.status(),
            cut.hausdorff(),
            cut.referenceIntervals(),
            cut.approximationElements());
      }
      for (ComparisonDiagnostics.ReferenceError error : row.diagnostics().referenceErrors()) {
        csv(
            errors,
            row.id(),
            error.time(),
            error.quality(),
            error.distanceToStaircase(),
            error.referenceError(),
            error.lower(),
            error.upper(),
            error.certificate());
      }
      if (row.evidenceFile() != null) {
        ComparisonEngine.Payload payload = readPayload(directory, row);
        if (payload.cae() != null) {
          for (RefinementEvent event : payload.cae().history()) {
            csv(
                curves,
                row.id(),
                event.refinement(),
                event.currentBound(),
                event.bestBound(),
                event.activeCells(),
                event.selection());
          }
        }
      }
    }
    Files.writeString(directory.resolve("outcomes.csv"), outcomes, StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("measurements.csv"), timings, StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("alpha-diagnostics.csv"), cuts, StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("reference-errors.csv"), errors, StandardCharsets.UTF_8);
    Files.writeString(directory.resolve("refinement-curves.csv"), curves, StandardCharsets.UTF_8);
  }

  private static void csv(StringBuilder output, Object... fields) {
    for (int index = 0; index < fields.length; index++) {
      if (index > 0) {
        output.append(',');
      }
      String value = fields[index] == null ? "" : fields[index].toString();
      if (value.contains(",")
          || value.contains("\"")
          || value.contains("\n")
          || value.contains("\r")) {
        output.append('"').append(value.replace("\"", "\"\"")).append('"');
      } else {
        output.append(value);
      }
    }
    output.append('\n');
  }
}
