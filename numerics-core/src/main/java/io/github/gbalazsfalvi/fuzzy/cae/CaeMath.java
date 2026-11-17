package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Numeric canonicalization and shape checks; mathematical validity is checked by recomputation. */
final class CaeMath {
  private CaeMath() {}

  static BigDecimal nonnegative(BigDecimal value) {
    value = Objects.requireNonNull(value, "bound").stripTrailingZeros();
    if (value.signum() < 0) {
      throw new IllegalArgumentException("A numerical bound must be nonnegative.");
    }
    return value;
  }

  static List<BigDecimal> bounds(List<BigDecimal> values) {
    List<BigDecimal> result = values.stream().map(CaeMath::nonnegative).toList();
    if (result.isEmpty()
        || result.stream().anyMatch(value -> value.compareTo(BigDecimal.ONE) > 0)) {
      throw new IllegalArgumentException(
          "Endograph bounds must form a nonempty sequence in [0,1].");
    }
    return result;
  }

  static BigDecimal maximum(List<BigDecimal> values) {
    return values.stream().max(BigDecimal::compareTo).orElseThrow();
  }
}
