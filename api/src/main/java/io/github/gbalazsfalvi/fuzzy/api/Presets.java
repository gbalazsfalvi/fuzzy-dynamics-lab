package io.github.gbalazsfalvi.fuzzy.api;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.*;
import io.github.gbalazsfalvi.fuzzy.membership.*;
import java.util.List;
import java.util.Map;

/** Closed registry of exact problems accepted by the local workbench. */
final class Presets {
  private static final List<Preset> ALL =
      List.of(
          new Preset(
              "affine",
              "Affine contraction",
              new AffineContraction(Rational.of(1, 2)),
              Map.of("factor", "1/2"),
              List.of(peak("1/2", "1/3", "1"))),
          new Preset(
              "tent",
              "Tent map, separated peaks",
              new TentMap(),
              Map.of(),
              List.of(peak("1/4", "1/5", "1"), peak("3/4", "1/6", "3/5"))),
          new Preset(
              "logistic",
              "Logistic map",
              new LogisticMap(Rational.of(4)),
              Map.of("parameter", "4"),
              List.of(peak("1/2", "1/3", "1"))),
          new Preset(
              "coupled-tent",
              "Coupled tent map",
              new CoupledTentMap(Rational.of(1, 4)),
              Map.of("coupling", "1/4"),
              List.of(new TentPeak(ExactPoint.of("1/3", "2/3"), Rational.of(1, 3), Rational.ONE))));

  private Presets() {}

  static Preset get(String id) {
    return ALL.stream()
        .filter(preset -> preset.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown presetId."));
  }

  static List<Map<String, Object>> descriptions() {
    return ALL.stream().map(Preset::description).toList();
  }

  private static TentPeak peak(String center, String width, String height) {
    return new TentPeak(ExactPoint.of(center), Rational.parse(width), Rational.parse(height));
  }

  record Preset(
      String id,
      String label,
      CertifiedMap map,
      Map<String, String> parameters,
      List<TentPeak> peaks) {
    CaeProblem problem(int horizon) {
      return new CaeProblem(map, new MultiPeakMembership(peaks), horizon);
    }

    Map<String, Object> description() {
      return Map.of(
          "id",
          id,
          "label",
          label,
          "dimension",
          map.dimension(),
          "map",
          Map.of("family", id, "parameters", parameters),
          "profile",
          Map.of(
              "aggregation",
              "MAXIMUM",
              "peaks",
              peaks.stream()
                  .map(
                      peak ->
                          Map.of(
                              "center",
                              peak.center().coordinates().stream().map(Object::toString).toList(),
                              "width",
                              peak.width().toString(),
                              "height",
                              peak.height().toString()))
                  .toList()));
    }
  }
}
