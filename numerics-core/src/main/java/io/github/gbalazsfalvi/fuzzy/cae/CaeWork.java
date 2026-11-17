package io.github.gbalazsfalvi.fuzzy.cae;

/**
 * Actual numerical calls attempted by a solver, including discarded partial refinements.
 *
 * @param cellAttempts the number of cell computations started
 * @param mapImages the number of interval image calls attempted
 * @param lipschitzBounds the number of spatial Lipschitz queries attempted
 * @param membershipRanges the number of membership range calls attempted
 * @param membershipLipschitzBounds the number of membership Lipschitz queries attempted
 */
public record CaeWork(
    long cellAttempts,
    long mapImages,
    long lipschitzBounds,
    long membershipRanges,
    long membershipLipschitzBounds) {
  /**
   * Rejects negative counters.
   *
   * @param cellAttempts the cell attempts
   * @param mapImages the image attempts
   * @param lipschitzBounds the spatial bound attempts
   * @param membershipRanges the membership range attempts
   * @param membershipLipschitzBounds the membership bound attempts
   * @throws IllegalArgumentException if any counter is negative
   */
  public CaeWork {
    if (cellAttempts < 0
        || mapImages < 0
        || lipschitzBounds < 0
        || membershipRanges < 0
        || membershipLipschitzBounds < 0) {
      throw new IllegalArgumentException("Work counters must be nonnegative.");
    }
  }
}
