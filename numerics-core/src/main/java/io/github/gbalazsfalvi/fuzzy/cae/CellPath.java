package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.geometry.Box;
import java.util.Objects;

/**
 * A binary path through exact longest side bisections of the complete unit domain.
 *
 * @param bits the zero or one branch sequence; empty means the root
 */
public record CellPath(String bits) {
  /**
   * Validates the binary sequence.
   *
   * @param bits the binary path
   * @throws IllegalArgumentException if a character is not zero or one
   */
  public CellPath {
    Objects.requireNonNull(bits, "bits");
    if (bits.chars().anyMatch(value -> value != '0' && value != '1')) {
      throw new IllegalArgumentException("A cell path may contain only zero and one.");
    }
  }

  /**
   * Returns the initial domain path.
   *
   * @return the empty root path
   */
  public static CellPath root() {
    return new CellPath("");
  }

  /**
   * Returns the refinement depth.
   *
   * @return the binary path length
   */
  public int depth() {
    return bits.length();
  }

  /**
   * Appends a child branch.
   *
   * @param branch zero for the first child or one for the second
   * @return the child path
   * @throws IllegalArgumentException if the branch is not zero or one
   */
  public CellPath child(int branch) {
    if (branch < 0 || branch > 1) {
      throw new IllegalArgumentException("A binary branch must be zero or one.");
    }
    return new CellPath(bits + branch);
  }

  /**
   * Reconstructs the exact cell using depth rotated tie breaking.
   *
   * @param dimension the positive domain dimension
   * @return the closed cell, including shared split boundaries
   */
  public Box box(int dimension) {
    Box result = Box.unit(dimension);
    for (int depth = 0; depth < bits.length(); depth++) {
      result = result.bisectLongest(depth % dimension).get(bits.charAt(depth) - '0');
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return bits.isEmpty() ? "root" : bits;
  }
}
