package io.github.gbalazsfalvi.fuzzy.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JobRequestTest {
  static final String VALID =
      """
      {"presetId":"affine","strategy":"MAXIMUM_BOUND","precision":34,"horizon":2,
       "tolerance":"0.2","maxRefinements":8,"maxCells":9,"maxDepth":8}
      """;

  @Test
  void validatesBeforeAllocatingNumericalWork() {
    assertEquals("0.2", JobRequest.parse(VALID).tolerance());
    for (String invalid :
        new String[] {
          VALID.replace("\"0.2\"", "0.2"),
          VALID.replace("\"0.2\"", "\"1e-999999999\""),
          VALID.replace("\"horizon\":2", "\"horizon\":2000"),
          VALID.replace("\"precision\":34", "\"precision\":34000"),
          VALID.replace("\"maxCells\":9", "\"maxCells\":1000000"),
          VALID.replace("\"affine\"", "\"../file\""),
          VALID.replace("\"MAXIMUM_BOUND\"", "\"CLASS\"")
        }) {
      assertThrows(IllegalArgumentException.class, () -> JobRequest.parse(invalid));
    }
  }
}
