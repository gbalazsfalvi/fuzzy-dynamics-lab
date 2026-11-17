package io.github.gbalazsfalvi.fuzzy.maps;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;

/** Shared algebra for maps with independently justified unit domain invariance. */
final class MapSupport {
  private MapSupport() {}

  static Box invariantImage(DecimalInterval... axes) {
    // Intersecting is sound because each built in map is analytically a unit self map.
    return Box.of(axes)
        .intersection(Box.unit(axes.length))
        .orElseThrow(
            () -> new IllegalStateException("A map enclosure violated its unit invariant."));
  }

  static DecimalInterval tentImage(DecimalInterval input, DecimalArithmetic arithmetic) {
    DecimalInterval one = arithmetic.enclose(Rational.ONE);
    DecimalInterval two = arithmetic.enclose(Rational.TWO);
    return arithmetic.subtract(
        one, arithmetic.abs(arithmetic.subtract(arithmetic.multiply(two, input), one)));
  }

  static Rational tent(Rational input) {
    return Rational.ONE.subtract(Rational.TWO.multiply(input).subtract(Rational.ONE).abs());
  }
}
