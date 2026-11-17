package io.github.gbalazsfalvi.fuzzy.maps;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import java.math.BigDecimal;
import java.util.List;

/** The continuous folding map f(x) = 1 - abs(2*x - 1), with Lipschitz constant two. */
public final class TentMap implements CertifiedMap {
  /** Creates the parameter free tent map. */
  public TentMap() {}

  /** {@inheritDoc} */
  @Override
  public String id() {
    return "tent";
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public Box image(Box input, DecimalArithmetic arithmetic) {
    UnitDomain.require(input, dimension());
    return MapSupport.invariantImage(MapSupport.tentImage(input.axis(0), arithmetic));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal lipschitzBound(Box region, DecimalArithmetic arithmetic) {
    UnitDomain.require(region, dimension());
    return BigDecimal.TWO;
  }

  /** {@inheritDoc} */
  @Override
  public ExactPoint evaluateExact(ExactPoint input) {
    UnitDomain.require(input, dimension());
    return new ExactPoint(List.of(MapSupport.tent(input.coordinate(0))));
  }
}
