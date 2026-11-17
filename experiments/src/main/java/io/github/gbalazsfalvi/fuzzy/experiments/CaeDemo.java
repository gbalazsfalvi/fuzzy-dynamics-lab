package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs or independently reloads the local milestone 02 evidence archive.
 *
 * <p>Examples include both successful certification and honest resource exits. This is an auditable
 * functional demonstration; no wall time comparison or algorithmic superiority is inferred.
 */
public final class CaeDemo {
  private CaeDemo() {}

  /**
   * Generates the JSON and Markdown reports, or verifies an existing JSON without overwriting it.
   *
   * @param args no arguments, --output path, or --verify path
   * @throws IOException if evidence or source provenance cannot be read or written
   * @throws IllegalArgumentException if arguments, certificates, or replay are invalid
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 0
        && (args.length != 2 || (!"--output".equals(args[0]) && !"--verify".equals(args[0])))) {
      throw new IllegalArgumentException("Usage: caeDemo [--output <path> | --verify <path>]");
    }
    Path root = Path.of("").toAbsolutePath();
    Path file = Path.of(args.length == 0 ? "artifacts/cae-demo.json" : args[1]);
    if (args.length == 2 && "--verify".equals(args[0])) {
      CaeEvidence.Archive archive = CaeReports.read(file);
      if (!archive.sourceSha256().equals(FoundationDemo.sourceDigest(root))) {
        throw new IllegalArgumentException(
            "Source digest changed. Review the report's provenance.");
      }
      int checked = CaeReports.verify(archive);
      System.out.println(
          "Verified "
              + checked
              + " stored snapshots and "
              + archive.cases().size()
              + " full deterministic runs: "
              + file.toAbsolutePath());
      return;
    }
    CaeEvidence.Archive archive = runExamples(root);
    CaeReports.verify(archive);
    CaeReports.write(archive, file);
    for (CaeEvidence.Case evidence : archive.cases()) {
      CaeResult result = evidence.outcome().toCore();
      System.out.println(
          evidence.id()
              + ": "
              + result.reason()
              + ", bound="
              + result.best().orElseThrow().worstBound().toPlainString()
              + ", cells="
              + result.last().orElseThrow().cells().size());
    }
    System.out.println("CAE evidence: " + file.toAbsolutePath());
    System.out.println("Readable summary: " + CaeReports.summaryPath(file).toAbsolutePath());
  }

  static CaeEvidence.Archive runExamples(Path root) throws IOException {
    List<CaeEvidence.Case> cases = new ArrayList<>();
    add(
        cases,
        "affine-separated-peaks",
        new AffineContraction(Rational.of(3, 4)),
        List.of(peak("1/4", "1/8", "1"), peak("3/4", "1/8", "1")),
        6,
        settings(34, "0.1", 128, 24));
    add(
        cases,
        "tent-strong-and-weak",
        new TentMap(),
        List.of(peak("1/5", "1/10", "1"), peak("4/5", "2/25", "1/100")),
        3,
        settings(34, "0.15", 96, 24));
    add(
        cases,
        "logistic-turning-point",
        new LogisticMap(Rational.of(37, 10)),
        List.of(peak("2/5", "1/5", "1")),
        3,
        settings(34, "0.15", 256, 24));
    add(
        cases,
        "coupled-square",
        new CoupledTentMap(Rational.of(1, 3)),
        List.of(
            new TentPeak(ExactPoint.of("1/4", "3/4"), Rational.of(1, 5), Rational.ONE),
            new TentPeak(ExactPoint.of("3/4", "1/4"), Rational.of(3, 20), Rational.of(2, 5))),
        2,
        settings(34, "0.25", 300, 24));
    add(
        cases,
        "expansion-budget-exit",
        new LogisticMap(Rational.of(37, 10)),
        List.of(peak("2/5", "1/5", "1")),
        10,
        settings(34, "0.001", 8, 24));
    add(
        cases,
        "low-precision-depth-exit",
        new LogisticMap(Rational.of(37, 10)),
        List.of(peak("1/3", "1/7", "2/3")),
        2,
        settings(1, "0.0001", 200, 6));
    add(
        cases,
        "refinement-can-worsen-error",
        new AffineContraction(Rational.of(3, 4)),
        List.of(peak("1/2", "1", "1/2"), peak("1/10", "1/20", "1")),
        0,
        settings(34, "0.1", 1, 24));
    return new CaeEvidence.Archive(
        CaeEvidence.SCHEMA,
        CaeEvidence.SCOPE,
        Instant.now().toString(),
        FoundationDemo.sourceDigest(root),
        cases);
  }

  private static void add(
      List<CaeEvidence.Case> cases,
      String id,
      CertifiedMap map,
      List<TentPeak> peaks,
      int horizon,
      CaeSettings settings) {
    CaeProblem problem = new CaeProblem(map, new MultiPeakMembership(peaks), horizon);
    CaeResult result = new CaeSolver().solve(problem, settings);
    cases.add(
        new CaeEvidence.Case(
            id, CaeEvidence.Problem.from(problem), settings, CaeEvidence.Outcome.from(result)));
  }

  private static TentPeak peak(String center, String width, String height) {
    return new TentPeak(ExactPoint.of(center), Rational.parse(width), Rational.parse(height));
  }

  private static CaeSettings settings(int precision, String tolerance, int splits, int depth) {
    return new CaeSettings(
        new DecimalArithmetic(precision),
        new BigDecimal(tolerance),
        splits,
        splits + 1,
        depth,
        5,
        CaeSettings.Strategy.MAXIMUM_BOUND);
  }
}
