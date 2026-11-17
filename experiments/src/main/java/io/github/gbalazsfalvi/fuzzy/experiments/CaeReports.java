package io.github.gbalazsfalvi.fuzzy.experiments;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSnapshot;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CaeVerification;
import io.github.gbalazsfalvi.fuzzy.cae.CertificateVerifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exact decimal evidence IO and strict local replay; never regenerates an input before checking it.
 */
final class CaeReports {
  private static final Gson JSON = EvidenceJson.FORMAT;

  private CaeReports() {}

  static void write(CaeEvidence.Archive archive, Path output) throws IOException {
    Path target = output.toAbsolutePath().normalize();
    Files.createDirectories(target.getParent());
    Files.writeString(target, encode(archive) + "\n", StandardCharsets.UTF_8);
    Files.writeString(summaryPath(target), summary(archive), StandardCharsets.UTF_8);
  }

  static CaeEvidence.Archive read(Path input) throws IOException {
    return decode(Files.readString(input, StandardCharsets.UTF_8));
  }

  static String encode(CaeEvidence.Archive archive) {
    return JSON.toJson(archive);
  }

  static CaeEvidence.Archive decode(String json) {
    CaeEvidence.Archive archive = JSON.fromJson(json, CaeEvidence.Archive.class);
    if (archive == null) {
      throw new JsonParseException("A CAE archive cannot be null.");
    }
    return archive;
  }

  static int verify(CaeEvidence.Archive archive) {
    int certificates = 0;
    for (CaeEvidence.Case evidence : archive.cases()) {
      CaeProblem problem = evidence.problem().toCore();
      CaeResult claimed = evidence.outcome().toCore();
      if (claimed.status() != CaeResult.Status.CERTIFIED
          && claimed.status() != CaeResult.Status.BUDGET_EXHAUSTED) {
        throw new IllegalArgumentException(
            "Demo archives only support completed deterministic runs.");
      }
      for (CaeSnapshot snapshot :
          List.of(claimed.best().orElseThrow(), claimed.last().orElseThrow())) {
        CaeVerification verification =
            new CertificateVerifier().verify(problem, evidence.settings(), snapshot);
        if (!verification.valid()) {
          throw new IllegalArgumentException(
              "Certificate rejected for "
                  + evidence.id()
                  + ": "
                  + String.join(" ", verification.issues()));
        }
        certificates++;
      }
      // Snapshot replay checks the mathematics. Full deterministic replay additionally checks the
      // selection trace, stopping condition, incumbent, and actual operation counters.
      CaeResult replayed = new CaeSolver().solve(problem, evidence.settings());
      if (!claimed.equals(replayed)) {
        throw new IllegalArgumentException(
            "The full run differs from deterministic replay: " + evidence.id());
      }
    }
    return certificates;
  }

  static Path summaryPath(Path output) {
    String name = output.getFileName().toString();
    String stem = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    return output.resolveSibling(stem + "-summary.md");
  }

  private static String summary(CaeEvidence.Archive archive) {
    StringBuilder text =
        new StringBuilder("# CAE milestone 02 evidence\n\n")
            .append(archive.scope())
            .append("\n\n")
            .append("Generated: ")
            .append(archive.generatedAtUtc())
            .append("\n\n")
            .append("Source SHA-256: ")
            .append((char) 96)
            .append(archive.sourceSha256())
            .append((char) 96)
            .append("\n\n")
            .append(
                "| Case | Outcome | Best horizon bound | Best / last cells | Completed splits | Cell attempts |\n")
            .append("| --- | --- | ---: | ---: | ---: | ---: |\n");
    for (CaeEvidence.Case evidence : archive.cases()) {
      CaeResult result = evidence.outcome().toCore();
      text.append("| ")
          .append(evidence.id())
          .append(" | ")
          .append(result.reason())
          .append(" | ")
          .append(result.best().orElseThrow().worstBound().toPlainString())
          .append(" | ")
          .append(result.best().orElseThrow().cells().size())
          .append(" / ")
          .append(result.last().orElseThrow().cells().size())
          .append(" | ")
          .append(result.history().size())
          .append(" | ")
          .append(result.work().cellAttempts())
          .append(" |\n");
    }
    return text.append("\nEvery time from zero through the declared horizon is covered. ")
        .append(
            "CERTIFIED means the requested tolerance was reached; a budget exit retains a valid ")
        .append("upper bound but does not claim that tolerance.\n\n")
        .append("Replay reconstructs the full initial partition, recomputes both snapshots, and ")
        .append(
            "reruns every deterministic refinement. It shares the trusted interval implementation ")
        .append(
            "with generation; the independent exact continuous and rational tests remain essential.\n\n")
        .append(
            "The JSON stores decimal values as strings and model parameters as exact fractions. ")
        .append("The digest records source provenance; it is not a digital signature.\n")
        .toString();
  }
}
