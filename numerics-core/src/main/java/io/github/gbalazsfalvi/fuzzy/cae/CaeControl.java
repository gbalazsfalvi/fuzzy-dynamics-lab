package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Tracks every attempted numerical operation and checks cancellation between operations. */
final class CaeControl {
  private final BooleanSupplier cancelled;
  private long cells;
  private long images;
  private long lipschitz;
  private long ranges;
  private long membershipLipschitz;

  CaeControl(BooleanSupplier cancelled) {
    this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
  }

  void check() {
    if (cancelled.getAsBoolean()) {
      throw new Cancelled();
    }
  }

  void cell() {
    check();
    cells = Math.incrementExact(cells);
  }

  Box image(CaeProblem problem, Box input, DecimalArithmetic arithmetic) {
    check();
    images = Math.incrementExact(images);
    return problem.map().image(input, arithmetic);
  }

  BigDecimal lipschitz(CaeProblem problem, Box region, DecimalArithmetic arithmetic) {
    check();
    lipschitz = Math.incrementExact(lipschitz);
    return problem.map().lipschitzBound(region, arithmetic);
  }

  DecimalInterval range(CaeProblem problem, Box input, DecimalArithmetic arithmetic) {
    check();
    ranges = Math.incrementExact(ranges);
    return problem.membership().range(input, arithmetic);
  }

  BigDecimal membershipLipschitz(CaeProblem problem, DecimalArithmetic arithmetic) {
    check();
    membershipLipschitz = Math.incrementExact(membershipLipschitz);
    return problem.membership().lipschitzBound(arithmetic);
  }

  CaeWork work() {
    return new CaeWork(cells, images, lipschitz, ranges, membershipLipschitz);
  }

  /** Internal control flow exception; partial child computations never mutate the partition. */
  static final class Cancelled extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Cancelled() {
      super("CAE cancellation requested.", null, false, false);
    }
  }
}
