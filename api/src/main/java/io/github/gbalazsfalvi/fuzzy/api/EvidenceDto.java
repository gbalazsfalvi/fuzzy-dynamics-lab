package io.github.gbalazsfalvi.fuzzy.api;

import io.github.gbalazsfalvi.fuzzy.cae.*;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit projection prevents decimal JSON numbers and accidental exposure of core internals. */
final class EvidenceDto {
  private EvidenceDto() {}

  static Map<String, Object> result(CaeResult result, long elapsed, long verification) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("status", result.status().name());
    dto.put("reason", result.reason().name());
    dto.put("detail", result.detail());
    dto.put("verified", true);
    dto.put("elapsedNanos", Long.toString(elapsed));
    dto.put("verificationNanos", Long.toString(verification));
    dto.put(
        "worstBound", result.best().map(snapshot -> snapshot.worstBound().toString()).orElse(null));
    dto.put("bounds", result.best().map(snapshot -> decimals(snapshot.bounds())).orElse(List.of()));
    dto.put("best", result.best().map(EvidenceDto::snapshot).orElse(null));
    dto.put("last", result.last().map(EvidenceDto::snapshot).orElse(null));
    dto.put(
        "refinementCurves",
        result.history().stream()
            .map(
                event ->
                    Map.of(
                        "refinement",
                        event.refinement(),
                        "parent",
                        event.parent().bits(),
                        "selection",
                        event.selection().name(),
                        "currentBound",
                        event.currentBound().toString(),
                        "bestBound",
                        event.bestBound().toString(),
                        "activeCells",
                        event.activeCells()))
            .toList());
    return dto;
  }

  private static Map<String, Object> snapshot(CaeSnapshot snapshot) {
    return Map.of(
        "refinement",
        snapshot.refinement(),
        "cellCount",
        snapshot.cells().size(),
        "worstBound",
        snapshot.worstBound().toString(),
        "bounds",
        decimals(snapshot.bounds()),
        "cells",
        snapshot.cells().stream().map(EvidenceDto::cell).toList());
  }

  private static Map<String, Object> cell(CaeCell cell) {
    var membership = cell.membership();
    return Map.of(
        "path",
        cell.path().bits(),
        "initialBox",
        box(cell.initial()),
        "membership",
        Map.of(
            "grade",
            membership.grade().toString(),
            "gradeError",
            membership.gradeError().toString(),
            "variation",
            membership.variation().toString(),
            "upperMembership",
            membership.upperMembership().toString()),
        "bounds",
        decimals(cell.bounds()),
        "trajectory",
        cell.trajectory().stream()
            .map(
                step ->
                    Map.of(
                        "time",
                        step.time(),
                        "representative",
                        decimals(step.representative().coordinates()),
                        "reachable",
                        box(step.reachable()),
                        "radius",
                        step.radius().toString(),
                        "directRadius",
                        step.directRadius().toString(),
                        "propagatedRadius",
                        step.propagatedRadius().toString(),
                        "transitionLipschitz",
                        step.transitionLipschitz().toString(),
                        "evaluationResidual",
                        step.evaluationResidual().toString()))
            .toList());
  }

  private static List<String> decimals(List<BigDecimal> values) {
    return values.stream().map(BigDecimal::toString).toList();
  }

  private static List<Map<String, String>> box(Box box) {
    return box.axes().stream()
        .map(axis -> Map.of("lower", axis.lower().toString(), "upper", axis.upper().toString()))
        .toList();
  }
}
