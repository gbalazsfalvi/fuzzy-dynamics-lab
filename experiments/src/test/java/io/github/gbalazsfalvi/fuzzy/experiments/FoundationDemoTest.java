package io.github.gbalazsfalvi.fuzzy.experiments;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FoundationDemoTest {
  @TempDir Path directory;

  @Test
  void exportsParseableExactEvidenceAndTracksSourceChanges() throws IOException {
    Path source = directory.resolve("source");
    Files.createDirectories(source.resolve("numerics-core/src/main/java"));
    Path input = source.resolve("numerics-core/src/main/java/Example.java");
    Files.writeString(input, "class Example {}\n");
    Path output = directory.resolve("reports/foundation.json");
    FoundationDemo.writeReport(source, output);
    JsonObject report = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
    assertEquals("PASSED", report.get("status").getAsString());
    assertEquals("1/100", report.get("weakComponentEndographDistance").getAsString());
    assertEquals(4, report.getAsJsonArray("maps").size());
    assertEquals(34, report.getAsJsonObject("arithmetic").get("precision").getAsInt());
    assertTrue(
        report.getAsJsonObject("checks").entrySet().stream()
            .allMatch(e -> e.getValue().getAsBoolean()));
    report
        .getAsJsonArray("maps")
        .forEach(
            value -> {
              JsonObject map = value.getAsJsonObject();
              assertEquals(63, map.get("exactProbes").getAsInt());
              assertEquals(0, map.get("failedProbes").getAsInt());
              assertEquals(7, map.getAsJsonArray("steps").size());
            });
    String digest = FoundationDemo.sourceDigest(source);
    assertTrue(digest.matches("[0-9a-f]{64}"));
    assertEquals(digest, report.get("sourceSha256").getAsString());
    assertEquals(digest, FoundationDemo.sourceDigest(source));
    Files.writeString(input, "class Example { int changed; }\n");
    assertNotEquals(digest, FoundationDemo.sourceDigest(source));
  }

  @Test
  void rejectsUnknownArgumentsAndMissingSourceProvenance() {
    assertThrows(IllegalArgumentException.class, () -> FoundationDemo.main(new String[] {"--bad"}));
    assertThrows(
        IllegalArgumentException.class, () -> FoundationDemo.main(new String[] {"--bad", "file"}));
    assertThrows(IOException.class, () -> FoundationDemo.sourceDigest(directory));
  }
}
