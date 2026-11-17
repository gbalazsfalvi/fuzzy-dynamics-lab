package io.github.gbalazsfalvi.fuzzy.experiments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local training label export with explicit test split exclusion. */
public final class LearningDemo {
  private LearningDemo() {}

  /**
   * Exports signed complete trial-split labels and their actual numerical cost.
   *
   * @param args none, or --manifest file --output directory
   * @throws IOException if the frozen manifest or output cannot be read or written
   * @throws IllegalArgumentException if arguments or split definitions are invalid
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 0
        && (args.length != 4 || !"--manifest".equals(args[0]) || !"--output".equals(args[2]))) {
      throw new IllegalArgumentException(
          "Usage: learningDemo [--manifest <file> --output <directory>]");
    }
    Path manifest =
        Path.of(
            args.length == 0
                ? "research/protocols/learning-and-evaluation-split-v1.json"
                : args[1]);
    Path output = Path.of(args.length == 0 ? "artifacts/learning/labels" : args[3]);
    LearningProtocol protocol =
        EvidenceJson.FORMAT.fromJson(
            Files.readString(manifest, StandardCharsets.UTF_8), LearningProtocol.class);
    if (protocol == null) {
      throw new IllegalArgumentException("The frozen learning manifest cannot be null.");
    }
    long start = System.nanoTime();
    LearningData.Summary summary =
        LearningData.export(Path.of("").toAbsolutePath(), protocol, output, System.out::println);
    Files.writeString(
        output.resolve("generation-timing.json"),
        EvidenceJson.FORMAT.toJson(
                java.util.Map.of(
                    "elapsedNanoseconds",
                    System.nanoTime() - start,
                    "scope",
                    "Java exploration plus complete trial labels and CSV output"))
            + "\n",
        StandardCharsets.UTF_8);
    System.out.println(
        "Generated " + summary.rows() + " train/validation labels; no final test labels.");
  }
}
