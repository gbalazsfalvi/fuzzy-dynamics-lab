package io.github.gbalazsfalvi.fuzzy.fuzzy;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.geometry.UnitDomain;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * An exact finite reference state with zero membership everywhere outside its positive atoms.
 *
 * <p>Equal locations combine by maximum. Ordering is canonical, zero atoms are omitted, and no
 * normalization or convex hull operation is performed. This is not a sampled approximation of a
 * continuous profile unless a caller separately provides such an interpretation and its error.
 */
public final class FiniteFuzzySet {
  private final int dimension;
  private final NavigableMap<ExactPoint, Rational> grades;

  /**
   * Creates an immutable finite state, including an empty zero state.
   *
   * @param dimension the positive dimension
   * @param atoms the finite input atoms
   * @throws IllegalArgumentException if the dimension is invalid or an atom has another dimension
   */
  public FiniteFuzzySet(int dimension, List<FuzzyAtom> atoms) {
    if (dimension < 1) {
      throw new IllegalArgumentException("A finite state needs a positive dimension.");
    }
    this.dimension = dimension;
    TreeMap<ExactPoint, Rational> values = new TreeMap<>();
    for (FuzzyAtom atom : List.copyOf(atoms)) {
      UnitDomain.require(atom.location(), dimension);
      if (atom.grade().compareTo(Rational.ZERO) > 0) {
        values.merge(atom.location(), atom.grade(), Rational::max);
      }
    }
    grades = Collections.unmodifiableNavigableMap(values);
  }

  /**
   * Returns the state dimension.
   *
   * @return the positive dimension
   */
  public int dimension() {
    return dimension;
  }

  /**
   * Returns canonical immutable positive atoms.
   *
   * @return the positive atoms, ordered lexicographically by location
   */
  public List<FuzzyAtom> atoms() {
    return grades.entrySet().stream()
        .map(entry -> new FuzzyAtom(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * Reads membership at a point, returning zero off the positive support.
   *
   * @param point the exact location in the unit domain
   * @return its membership
   */
  public Rational membershipAt(ExactPoint point) {
    UnitDomain.require(point, dimension);
    return grades.getOrDefault(point, Rational.ZERO);
  }

  /**
   * Returns a complete positive alpha cut.
   *
   * @param alpha the exact level in (0,1]
   * @return the eligible points, possibly empty
   * @throws IllegalArgumentException if the level is outside (0,1]
   */
  public List<ExactPoint> alphaCut(Rational alpha) {
    if (alpha.compareTo(Rational.ZERO) <= 0 || alpha.compareTo(Rational.ONE) > 0) {
      throw new IllegalArgumentException("A positive alpha cut needs a level in (0,1].");
    }
    return grades.entrySet().stream()
        .filter(entry -> entry.getValue().compareTo(alpha) >= 0)
        .map(java.util.Map.Entry::getKey)
        .toList();
  }

  /**
   * Forms a pointwise maximum of two states.
   *
   * @param other the state of the same dimension
   * @return the maximum state
   * @throws IllegalArgumentException if dimensions differ
   */
  public FiniteFuzzySet maximum(FiniteFuzzySet other) {
    requireDimension(other.dimension);
    List<FuzzyAtom> combined = new ArrayList<>(atoms());
    combined.addAll(other.atoms());
    return new FiniteFuzzySet(dimension, combined);
  }

  /**
   * Computes the exact extension of a map over this finite positive support.
   *
   * @param map the equally dimensioned exact map
   * @return the exact finite image
   * @throws IllegalArgumentException if dimensions differ
   */
  public FiniteFuzzySet extend(CertifiedMap map) {
    requireDimension(map.dimension());
    return new FiniteFuzzySet(
        dimension,
        atoms().stream()
            .map(atom -> new FuzzyAtom(map.evaluateExact(atom.location()), atom.grade()))
            .toList());
  }

  private void requireDimension(int other) {
    if (dimension != other) {
      throw new IllegalArgumentException("Finite state dimensions must agree.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof FiniteFuzzySet state
            && dimension == state.dimension
            && grades.equals(state.grades));
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(dimension, grades);
  }
}
