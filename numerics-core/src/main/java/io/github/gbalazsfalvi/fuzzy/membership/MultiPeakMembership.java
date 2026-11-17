package io.github.gbalazsfalvi.fuzzy.membership;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.List;

/**
 * A pointwise maximum of peaks, allowing separated and nonconvex positive alpha cuts.
 *
 * @param peaks the nonempty immutable list of equally dimensioned peaks
 */
public record MultiPeakMembership(List<TentPeak> peaks) implements CertifiedMembership {
  /**
   * Copies peaks and checks dimension agreement.
   *
   * @param peaks the nonempty peak list
   * @throws IllegalArgumentException if the list is empty or dimensions differ
   */
  public MultiPeakMembership {
    peaks = List.copyOf(peaks);
    if (peaks.isEmpty()) {
      throw new IllegalArgumentException("At least one membership peak is required.");
    }
    int dimension = peaks.getFirst().dimension();
    if (peaks.stream().anyMatch(peak -> peak.dimension() != dimension)) {
      throw new IllegalArgumentException("Membership peak dimensions must agree.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return peaks.getFirst().dimension();
  }

  /** {@inheritDoc} */
  @Override
  public DecimalInterval range(Box input, DecimalArithmetic arithmetic) {
    UnitDomain.require(input, dimension());
    DecimalInterval result = arithmetic.enclose(Rational.ZERO);
    for (TentPeak peak : peaks) {
      result = arithmetic.maximum(result, peak.range(input, arithmetic));
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public Rational evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    Rational result = Rational.ZERO;
    for (TentPeak peak : peaks) {
      result = result.max(peak.evaluateExact(input));
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(DecimalArithmetic arithmetic) {
    BigDecimal result = BigDecimal.ZERO;
    for (TentPeak peak : peaks) {
      result = result.max(peak.lipschitzBound(arithmetic));
    }
    return result;
  }
}
