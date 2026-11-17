package io.github.gbalazsfalvi.fuzzy.maps;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import java.math.BigDecimal;

/**
 * A built in continuous self map of a normalized unit domain with explicit enclosure operations.
 *
 * <p>An implementation must justify image containment and its Lipschitz bound over the entire input
 * box. This contract does not turn an arbitrary sampled black box into a certified map.
 */
public sealed interface CertifiedMap
    permits AffineContraction, TentMap, LogisticMap, CoupledTentMap {
  /**
   * Identifies the map and its exact parameters.
   *
   * @return the deterministic identifier
   */
  String id();

  /**
   * Returns the state dimension.
   *
   * @return the positive dimension
   */
  int dimension();

  /**
   * Encloses the image of every point in an input box.
   *
   * @param input the entire box within the declared unit domain
   * @param arithmetic the directed rounding policy
   * @return the image enclosure within the unit domain
   * @throws IllegalArgumentException if the input dimension or domain is invalid
   */
  Box image(Box input, DecimalArithmetic arithmetic);

  /**
   * Bounds the infinity norm Lipschitz constant on the entire region.
   *
   * @param region the region within the declared unit domain
   * @param arithmetic the directed rounding policy
   * @return a nonnegative upper bound
   * @throws IllegalArgumentException if the region dimension or domain is invalid
   */
  BigDecimal lipschitzBound(Box region, DecimalArithmetic arithmetic);

  /**
   * Evaluates a small exact reference without decimal rounding.
   *
   * @param input the exact point within the declared unit domain
   * @return the exact image
   * @throws IllegalArgumentException if the point dimension or domain is invalid
   */
  ExactPoint evaluateExact(ExactPoint input);
}
