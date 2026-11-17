package io.github.gbalazsfalvi.fuzzy.api;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import java.math.BigDecimal;
import java.util.Set;

/** Validated finite request; mathematical tolerance remains exact decimal text. */
record JobRequest(
    String presetId,
    String strategy,
    int precision,
    int horizon,
    String tolerance,
    int maxRefinements,
    int maxCells,
    int maxDepth,
    String modelId) {
  private static final Set<String> FIELDS =
      Set.of(
          "presetId",
          "strategy",
          "precision",
          "horizon",
          "tolerance",
          "maxRefinements",
          "maxCells",
          "maxDepth",
          "modelId");

  static JobRequest parse(String json) {
    if (json.length() > 4096)
      throw new IllegalArgumentException("Request exceeds 4096 characters.");
    try {
      JsonObject object =
          new GsonBuilder()
              .setStrictness(Strictness.STRICT)
              .create()
              .fromJson(json, JsonObject.class);
      if (!FIELDS.containsAll(object.keySet()))
        throw new IllegalArgumentException("Unknown request field.");
      String tolerance = string(object, "tolerance");
      // Bound lexical size and exponent before BigDecimal arithmetic or precision allocation.
      if (!tolerance.matches(
          "(?:0?\\.[0-9]{1,64}|[1-9][0-9]{0,2}(?:\\.[0-9]{1,64})?)(?:[eE][+-]?[0-9]{1,2})?"))
        throw new IllegalArgumentException("Tolerance must be a short positive decimal string.");
      JobRequest request =
          new JobRequest(
              string(object, "presetId"),
              string(object, "strategy"),
              integer(object, "precision"),
              integer(object, "horizon"),
              tolerance,
              integer(object, "maxRefinements"),
              integer(object, "maxCells"),
              integer(object, "maxDepth"),
              object.has("modelId") && !object.get("modelId").isJsonNull()
                  ? string(object, "modelId")
                  : null);
      request.validate();
      return request;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Invalid request. Use the documented fields, types, and finite budgets.", exception);
    }
  }

  private void validate() {
    Presets.get(presetId);
    CaeSettings.Strategy.valueOf(strategy);
    if (precision < 16
        || precision > 64
        || horizon < 0
        || horizon > 20
        || maxRefinements < 0
        || maxRefinements > 512
        || maxCells < 1
        || maxCells > 513
        || maxDepth < 0
        || maxDepth > 24
        || (long) (2 * maxRefinements + 1) * (horizon + 1) * precision > 800_000
        || (long) maxCells * (horizon + 1) > 4096
        || new BigDecimal(tolerance).compareTo(new BigDecimal("1e-12")) < 0
        || new BigDecimal(tolerance).compareTo(BigDecimal.ONE) >= 0
        || (modelId != null && !modelId.matches("[a-z0-9-]{1,80}"))
        || (strategy.equals("RANKED") != (modelId != null)))
      throw new IllegalArgumentException("Settings exceed the local numerical safety budget.");
  }

  CaeSettings settings() {
    return new CaeSettings(
        new DecimalArithmetic(precision),
        new BigDecimal(tolerance),
        maxRefinements,
        maxCells,
        maxDepth,
        5,
        CaeSettings.Strategy.valueOf(strategy));
  }

  private static String string(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected string: " + key);
    return value.getAsString();
  }

  private static int integer(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null
        || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isNumber()
        || !value.getAsString().matches("[0-9]{1,6}"))
      throw new IllegalArgumentException("Expected bounded integer: " + key);
    return Integer.parseInt(value.getAsString());
  }
}
