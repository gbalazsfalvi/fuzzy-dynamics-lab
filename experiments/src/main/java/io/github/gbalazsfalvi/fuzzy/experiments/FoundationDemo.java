package io.github.gbalazsfalvi.fuzzy.experiments;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.fuzzy.EndographMetric;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FiniteFuzzySet;
import io.github.gbalazsfalvi.fuzzy.fuzzy.FuzzyAtom;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import io.github.gbalazsfalvi.fuzzy.trajectory.Trajectories;
import io.github.gbalazsfalvi.fuzzy.trajectory.TrajectoryStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs reproducible numerical foundation examples without a server or external service.
 *
 * <p>The report contains exact fractions and decimal strings so that downstream readers do not
 * silently round mathematical evidence through binary JSON numbers. It is a foundation smoke
 * demonstration, not a CAE benchmark or a continuous theorem proof.
 */
public final class FoundationDemo {
  private static final Gson JSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private FoundationDemo() {}

  /**
   * Writes the foundation evidence report from the project root.
   *
   * @param args either no arguments or --output followed by a local file path
   * @throws IOException if source provenance or the report cannot be read or written
   * @throws IllegalArgumentException if the argument syntax is invalid
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 0 && (args.length != 2 || !"--output".equals(args[0]))) {
      throw new IllegalArgumentException("Usage: foundationDemo [--output <path>]");
    }
    Path output = Path.of(args.length == 0 ? "artifacts/foundation-demo.json" : args[1]);
    writeReport(Path.of("").toAbsolutePath(), output);
    System.out.println("Numerical foundation checks passed. Report: " + output.toAbsolutePath());
  }

  static void writeReport(Path sourceRoot, Path output) throws IOException {
    DecimalArithmetic arithmetic = new DecimalArithmetic(34);
    List<MapEvidence> mapEvidence = new ArrayList<>();
    for (CertifiedMap map :
        List.of(
            new AffineContraction(Rational.of(3, 4)),
            new TentMap(),
            new LogisticMap(Rational.of(37, 10)),
            new CoupledTentMap(Rational.of(1, 3)))) {
      mapEvidence.add(evaluateMap(map, arithmetic));
    }

    FiniteFuzzySet source = state(atom("1/4", "1/5"), atom("3/4", "4/5"));
    FiniteFuzzySet collision = source.extend(new TentMap());
    FiniteFuzzySet strong = state(atom("1/5", "1"), atom("3/10", "1/2"));
    FiniteFuzzySet weak = strong.maximum(state(atom("9/10", "1/100")));
    Rational weakDistance = EndographMetric.distance(strong, weak);
    MultiPeakMembership profile =
        new MultiPeakMembership(
            List.of(
                new TentPeak(ExactPoint.of("1/4"), Rational.of(1, 8), Rational.ONE),
                new TentPeak(ExactPoint.of("3/4"), Rational.of(1, 8), Rational.of(1, 100))));

    Map<String, Boolean> checks = new LinkedHashMap<>();
    checks.put("allExactProbesEnclosed", mapEvidence.stream().allMatch(e -> e.failedProbes == 0));
    checks.put(
        "collisionUsesMaximum",
        collision.membershipAt(ExactPoint.of("1/2")).equals(Rational.of(4, 5)));
    checks.put("weakComponentDistanceIsOneHundredth", weakDistance.equals(Rational.of(1, 100)));
    checks.put(
        "separatedPeaksKeepTheGap",
        profile.evaluateExact(ExactPoint.of("1/2")).equals(Rational.ZERO));
    checks.put(
        "oneThirdIsOutwardEnclosed",
        arithmetic.enclose(Rational.of(1, 3)).contains(Rational.of(1, 3)));
    if (checks.values().stream().anyMatch(passed -> !passed)) {
      throw new IllegalStateException("A numerical foundation check failed.");
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schemaVersion", "1");
    report.put("milestone", "01");
    report.put("status", "PASSED");
    report.put("scope", "Numerical foundation examples; no adaptive CAE or learned method.");
    report.put("generatedAtUtc", Instant.now().toString());
    report.put("javaVersion", System.getProperty("java.version"));
    report.put("os", System.getProperty("os.name"));
    report.put(
        "arithmetic",
        Map.of("mode", "DIRECTED_DECIMAL_INTERVAL", "precision", arithmetic.precision()));
    report.put("sourceSha256", sourceDigest(sourceRoot));
    report.put("checks", checks);
    report.put("oneThirdEnclosure", endpoints(arithmetic.enclose(Rational.of(1, 3))));
    report.put("weakComponentEndographDistance", weakDistance.toString());
    report.put("maps", mapEvidence);
    Path target = output.toAbsolutePath().normalize();
    Files.createDirectories(target.getParent());
    Files.writeString(target, JSON.toJson(report) + "\n", StandardCharsets.UTF_8);
  }

  private static MapEvidence evaluateMap(CertifiedMap map, DecimalArithmetic arithmetic) {
    Box initial =
        map.dimension() == 1
            ? Box.of(DecimalInterval.of("0.12", "0.27"))
            : Box.of(DecimalInterval.of("0.12", "0.27"), DecimalInterval.of("0.63", "0.78"));
    int horizon = 6;
    List<TrajectoryStep> steps = Trajectories.enclose(map, initial, horizon, arithmetic);
    int failed = 0;
    int probes = 0;
    for (int sample = 0; sample <= 8; sample++) {
      Rational fraction = Rational.of(sample, 8);
      Rational x = Rational.of(12, 100).add(Rational.of(15, 100).multiply(fraction));
      ExactPoint point =
          map.dimension() == 1
              ? new ExactPoint(List.of(x))
              : new ExactPoint(
                  List.of(x, Rational.of(63, 100).add(Rational.of(15, 100).multiply(fraction))));
      List<ExactPoint> exact = Trajectories.exact(map, point, horizon);
      for (int time = 0; time <= horizon; time++) {
        probes++;
        if (!steps.get(time).reachable().contains(exact.get(time))) {
          failed++;
        }
      }
    }
    List<StepEvidence> evidence =
        steps.stream()
            .map(
                step ->
                    new StepEvidence(
                        step.time(),
                        step.representative().coordinates().stream()
                            .map(java.math.BigDecimal::toPlainString)
                            .toList(),
                        step.reachable().axes().stream().map(FoundationDemo::endpoints).toList(),
                        step.radius().toPlainString(),
                        step.evaluationResidual().toPlainString()))
            .toList();
    return new MapEvidence(map.id(), map.dimension(), probes, failed, evidence);
  }

  static String sourceDigest(Path root) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<Path> files = new ArrayList<>();
      for (String directory : List.of("numerics-core/src", "experiments/src", "gradle")) {
        Path source = root.resolve(directory);
        if (Files.isDirectory(source)) {
          try (var paths = Files.walk(source)) {
            files.addAll(
                paths
                    .filter(Files::isRegularFile)
                    .filter(
                        path ->
                            path.toString().endsWith(".java")
                                || path.toString().endsWith(".toml")
                                || path.toString().endsWith(".properties")
                                || path.toString().endsWith(".xml"))
                    .toList());
          }
        }
      }
      for (String buildFile :
          List.of(
              "build.gradle.kts",
              "settings.gradle.kts",
              "numerics-core/build.gradle.kts",
              "experiments/build.gradle.kts",
              "gradle.properties",
              "numerics-core/gradle.lockfile",
              "experiments/gradle.lockfile")) {
        if (Files.isRegularFile(root.resolve(buildFile))) {
          files.add(root.resolve(buildFile));
        }
      }
      if (files.isEmpty()) {
        throw new IOException(
            "Run the foundation demo from the project root to record source provenance.");
      }
      files.sort(
          java.util.Comparator.comparing(
              path -> root.relativize(path).toString().replace('\\', '/')));
      for (Path file : files) {
        digest.update(
            root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
        digest.update((byte) 0);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", exception);
    }
  }

  private static List<String> endpoints(DecimalInterval interval) {
    return List.of(interval.lower().toPlainString(), interval.upper().toPlainString());
  }

  private static FuzzyAtom atom(String location, String grade) {
    return new FuzzyAtom(ExactPoint.of(location), Rational.parse(grade));
  }

  private static FiniteFuzzySet state(FuzzyAtom... atoms) {
    return new FiniteFuzzySet(1, List.of(atoms));
  }

  /** A map example with independently computed exact rational probes. */
  private record MapEvidence(
      String map, int dimension, int exactProbes, int failedProbes, List<StepEvidence> steps) {}

  /** Decimal strings preserve the exact numeric content of each enclosure. */
  private record StepEvidence(
      int time,
      List<String> representative,
      List<List<String>> reachable,
      String radius,
      String evaluationResidual) {}
}
