package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * One numerical worker thread, randomized paired method order, explicit warmup, and saved outcomes.
 */
final class ComparisonRunner {
  private ComparisonRunner() {}

  static ComparisonEvidence.Archive run(
      Path sourceRoot, ComparisonProtocol protocol, Path outputDirectory, Consumer<String> progress)
      throws IOException {
    if (protocol.stage().equals("FROZEN_HELD_OUT")) {
      FrozenEvaluationGuard.require(sourceRoot, protocol, outputDirectory);
    }
    List<ComparisonEvidence.Run> runs = new ArrayList<>();
    String sourceHash = FoundationDemo.sourceDigest(sourceRoot);
    String protocolHash = EvidenceHashes.of(protocol);
    Random random = new Random(protocol.orderSeed());
    for (ComparisonProtocol.Problem definition : protocol.problems()) {
      CaeProblem problem = definition.definition().toCore();
      ComparisonDiagnostics.References references =
          ComparisonDiagnostics.prepare(protocol, definition);
      for (BigDecimal tolerance : protocol.tolerances()) {
        Map<String, Draft> drafts = new LinkedHashMap<>();
        for (ComparisonProtocol.Method method : protocol.methods()) {
          String id = runId(definition, method, tolerance);
          String relative =
              "runs/" + id + (protocol.stage().equals("EXPLORATORY_PILOT") ? ".json" : ".json.gz");
          drafts.put(method.id(), new Draft(method, relative));
        }
        // Warmup and timed repetitions use the same paired cohort, with a saved fixed order seed.
        for (int repetition = -protocol.warmups(definition);
            repetition < protocol.repetitions(definition);
            repetition++) {
          List<ComparisonProtocol.Method> order = new ArrayList<>(protocol.methods());
          Collections.shuffle(order, random);
          for (int index = 0; index < order.size(); index++) {
            ComparisonProtocol.Method method = order.get(index);
            if (ComparisonEngine.exclusion(method, problem) != null) {
              continue;
            }
            long start = System.nanoTime();
            ComparisonEngine.Payload payload =
                ComparisonEngine.solve(protocol, method, problem, tolerance);
            long solved = System.nanoTime();
            ComparisonEngine.Verification verification =
                ComparisonEngine.verify(protocol, method, problem, tolerance, payload);
            long verified = System.nanoTime();
            if (repetition >= 0) {
              Draft draft = drafts.get(method.id());
              draft.record(
                  outputDirectory,
                  payload,
                  verification,
                  new ComparisonEvidence.Timing(
                      repetition, index, solved - start, verified - solved, verified - start));
            }
          }
        }
        for (Draft draft : drafts.values()) {
          String id = runId(definition, draft.method, tolerance);
          String configuration =
              EvidenceHashes.of(
                  new ComparisonEvidence.Configuration(
                      protocolHash, definition, draft.method, tolerance));
          String excluded = ComparisonEngine.exclusion(draft.method, problem);
          ComparisonEvidence.Run row;
          if (excluded != null) {
            row =
                new ComparisonEvidence.Run(
                    id,
                    definition.id(),
                    draft.method.id(),
                    tolerance,
                    configuration,
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
          } else {
            ComparisonEngine.Payload payload =
                ComparisonReports.readPayload(outputDirectory, draft.relative, draft.digest);
            ComparisonDiagnostics.Data diagnostics =
                ComparisonDiagnostics.measure(payload, references);
            row =
                new ComparisonEvidence.Run(
                    id,
                    definition.id(),
                    draft.method.id(),
                    tolerance,
                    configuration,
                    payload.status(),
                    payload.reason(),
                    payload.bound(),
                    payload.geometrySize(),
                    payload.intervalWork(),
                    payload.exactPointEvaluations(),
                    draft.verification,
                    draft.timings,
                    diagnostics,
                    draft.relative,
                    draft.digest);
          }
          runs.add(row);
        }
        progress.accept(
            definition.id()
                + ", tolerance="
                + tolerance.toPlainString()
                + ": "
                + drafts.size()
                + " method outcomes recorded.");
      }
    }
    if (!sourceHash.equals(FoundationDemo.sourceDigest(sourceRoot))) {
      throw new IllegalStateException(
          "Numerical source or build inputs changed during the comparison.");
    }
    return new ComparisonEvidence.Archive(
        ComparisonEvidence.SCHEMA,
        Instant.now().toString(),
        sourceHash,
        protocolHash,
        protocol,
        environment(),
        runs);
  }

  static String runId(
      ComparisonProtocol.Problem problem, ComparisonProtocol.Method method, BigDecimal tolerance) {
    return problem.id()
        + "--"
        + method.id()
        + "--"
        + tolerance.stripTrailingZeros().toPlainString().replace('.', '-');
  }

  private static Map<String, String> environment() {
    Map<String, String> data = new LinkedHashMap<>();
    data.put("javaVersion", System.getProperty("java.version"));
    data.put("vm", System.getProperty("java.vm.name"));
    data.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    data.put("architecture", System.getProperty("os.arch"));
    data.put("cpu", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "not available"));
    data.put("logicalProcessors", Integer.toString(Runtime.getRuntime().availableProcessors()));
    data.put("maximumHeapBytes", Long.toString(Runtime.getRuntime().maxMemory()));
    data.put(
        "garbageCollectors",
        ManagementFactory.getGarbageCollectorMXBeans().stream()
            .map(bean -> bean.getName())
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown"));
    data.put("numericalWorkerThreads", "1");
    data.put("externalLoad", "Not controlled; exploratory local measurements.");
    data.put("coldStartup", "Excluded from in-process measurements; not separately measured.");
    data.put(
        "timedScope",
        "Solve plus post-solve verification; excludes diagnostics, reference preparation, and report IO.");
    return data;
  }

  private static final class Draft {
    private final ComparisonProtocol.Method method;
    private final List<ComparisonEvidence.Timing> timings = new ArrayList<>();
    private final String relative;
    private String digest;
    private ComparisonEngine.Verification verification;

    Draft(ComparisonProtocol.Method method, String relative) {
      this.method = method;
      this.relative = relative;
    }

    void record(
        Path directory,
        ComparisonEngine.Payload current,
        ComparisonEngine.Verification checked,
        ComparisonEvidence.Timing timing)
        throws IOException {
      if (digest != null
          && (!ComparisonReports.readPayload(directory, relative, digest).equals(current)
              || !verification.equals(checked))) {
        throw new IllegalStateException(
            "A deterministic method changed its result between timing repetitions.");
      }
      if (digest == null) {
        digest = ComparisonReports.writePayload(directory, relative, current);
      }
      verification = checked;
      timings.add(timing);
    }
  }
}
