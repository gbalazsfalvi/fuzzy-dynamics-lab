package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeWork;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed manifest and per run payloads; large numerical geometry lives in individually hashed files.
 */
final class ComparisonEvidence {
  static final String SCHEMA = "comparison-evidence-v1";

  private ComparisonEvidence() {}

  record Configuration(
      String protocolSha256,
      ComparisonProtocol.Problem problem,
      ComparisonProtocol.Method method,
      BigDecimal tolerance) {}

  record Archive(
      String schemaVersion,
      String generatedAtUtc,
      String sourceSha256,
      String protocolSha256,
      ComparisonProtocol protocol,
      Map<String, String> environment,
      List<Run> runs) {
    Archive {
      if (!SCHEMA.equals(schemaVersion)
          || !sourceSha256.matches("[0-9a-f]{64}")
          || !protocolSha256.equals(EvidenceHashes.of(protocol))) {
        throw new IllegalArgumentException("Comparison provenance or schema is invalid.");
      }
      Instant.parse(generatedAtUtc);
      environment = Map.copyOf(environment);
      runs = List.copyOf(runs);
      if (runs.isEmpty() || runs.stream().map(Run::id).distinct().count() != runs.size()) {
        throw new IllegalArgumentException(
            "The comparison manifest needs uniquely identified runs.");
      }
    }
  }

  record Run(
      String id,
      String problemId,
      String methodId,
      BigDecimal tolerance,
      String configurationSha256,
      String status,
      String reason,
      Rational maximumBound,
      int geometrySize,
      CaeWork algorithmIntervalWork,
      long algorithmExactPointEvaluations,
      ComparisonEngine.Verification verificationWork,
      List<Timing> timings,
      ComparisonDiagnostics.Data diagnostics,
      String evidenceFile,
      String evidenceSha256) {
    Run {
      if (id == null
          || !id.matches("[a-z0-9-]+")
          || problemId == null
          || methodId == null
          || !configurationSha256.matches("[0-9a-f]{64}")
          || geometrySize < 0
          || algorithmExactPointEvaluations < 0) {
        throw new IllegalArgumentException("Comparison run identity or numerical work is invalid.");
      }
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(algorithmIntervalWork, "algorithmIntervalWork");
      Objects.requireNonNull(verificationWork, "verificationWork");
      Objects.requireNonNull(diagnostics, "diagnostics");
      timings = List.copyOf(timings);
      boolean unavailable = "UNAVAILABLE".equals(status);
      if (unavailable != (evidenceFile == null)
          || unavailable != timings.isEmpty()
          || (unavailable
              ? evidenceSha256 != null
              : evidenceSha256 == null || !evidenceSha256.matches("[0-9a-f]{64}"))) {
        throw new IllegalArgumentException(
            "Unavailable methods cannot carry execution evidence or timings.");
      }
    }

    long medianTotalNanoseconds() {
      return timings.isEmpty()
          ? 0
          : timings.stream()
              .mapToLong(Timing::totalNanoseconds)
              .sorted()
              .skip(timings.size() / 2)
              .findFirst()
              .orElseThrow();
    }
  }

  record Timing(
      int repetition,
      int orderIndex,
      long solveNanoseconds,
      long verificationNanoseconds,
      long totalNanoseconds) {
    Timing {
      if (repetition < 0
          || orderIndex < 0
          || solveNanoseconds < 0
          || verificationNanoseconds < 0
          || totalNanoseconds < 0
          || totalNanoseconds < Math.addExact(solveNanoseconds, verificationNanoseconds)) {
        throw new IllegalArgumentException(
            "Timing observations must have consistent nonnegative durations.");
      }
    }
  }
}
