package io.github.gbalazsfalvi.fuzzy.cuts;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete exact cut trajectories on a finite level grid ending at one.
 *
 * <p>When the supplied cuts are the exact cuts of a true trajectory, the represented lower
 * staircase has uniform vertical error at most the largest level gap, and its endograph error is
 * bounded by that gap. The public data constructor checks shape, not mathematical provenance; the
 * compute factory establishes exact cuts for the supported models.
 *
 * @param levels the strictly increasing positive levels, ending at one
 * @param states the complete cut unions at each time, in level order
 * @param exactPointEvaluations the map endpoint and turning point work
 * @param intervalImages the transported interval component count
 */
public record AlphaCutTrajectory(
    List<Rational> levels,
    List<List<IntervalUnion>> states,
    long exactPointEvaluations,
    long intervalImages) {
  /**
   * Copies level and state data and checks their shape.
   *
   * @param levels the strictly increasing levels in (0,1], including one
   * @param states the nonempty sequence of complete cut lists
   * @param exactPointEvaluations the nonnegative exact map work
   * @param intervalImages the nonnegative interval component work
   * @throws IllegalArgumentException if levels, shape, or counts are invalid
   */
  public AlphaCutTrajectory {
    levels = validateLevels(levels);
    states = states.stream().map(List::copyOf).toList();
    int count = levels.size();
    if (states.isEmpty()
        || states.stream().anyMatch(state -> state.size() != count)
        || exactPointEvaluations < 0
        || intervalImages < 0) {
      throw new IllegalArgumentException("Alpha cut trajectory shape and work must be valid.");
    }
  }

  /**
   * Computes exact scalar trajectories on the requested finite alpha grid.
   *
   * @param problem the supported one dimensional problem
   * @param levels the increasing positive levels ending at one
   * @return exact cuts and actual model work, including the initial state
   * @throws IllegalArgumentException if dimensions or levels are unsupported
   */
  public static AlphaCutTrajectory compute(CaeProblem problem, List<Rational> levels) {
    levels = validateLevels(levels);
    if (problem.map().dimension() != 1) {
      throw new IllegalArgumentException(
          "Exact cut trajectories currently support scalar maps only.");
    }
    List<List<IntervalUnion>> states = new ArrayList<>();
    List<IntervalUnion> current =
        levels.stream().map(alpha -> ExactAlphaCuts.initial(problem.membership(), alpha)).toList();
    states.add(current);
    long evaluations = 0;
    long images = 0;
    for (int time = 1; time <= problem.horizon(); time++) {
      List<IntervalUnion> next = new ArrayList<>();
      for (IntervalUnion cut : current) {
        ExactAlphaCuts.Image image = ExactAlphaCuts.image(problem.map(), cut);
        next.add(image.cuts());
        evaluations = Math.addExact(evaluations, image.exactPointEvaluations());
        images = Math.addExact(images, image.intervalImages());
      }
      current = List.copyOf(next);
      states.add(current);
    }
    return new AlphaCutTrajectory(levels, states, evaluations, images);
  }

  /**
   * Creates a uniform positive level grid, including one.
   *
   * @param count the positive number of levels
   * @return levels 1/count through one
   * @throws IllegalArgumentException if the count is not positive
   */
  public static List<Rational> uniformLevels(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("An alpha grid needs at least one positive level.");
    }
    List<Rational> levels = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      levels.add(Rational.of(index + 1L, count));
    }
    return List.copyOf(levels);
  }

  /**
   * Returns the maximum vertical level gap.
   *
   * <p>This is an error bound relative to a true trajectory only when every supplied cut is its
   * exact positive alpha cut, as established by compute or independent verification. Arbitrary
   * caller-supplied state data does not acquire a certificate from this numeric gap alone.
   *
   * @return the largest consecutive level gap, counting zero as the baseline level
   */
  public Rational errorBound() {
    Rational previous = Rational.ZERO;
    Rational maximum = Rational.ZERO;
    for (Rational level : levels) {
      maximum = maximum.max(level.subtract(previous));
      previous = level;
    }
    return maximum;
  }

  private static List<Rational> validateLevels(List<Rational> levels) {
    levels = List.copyOf(levels);
    if (levels.isEmpty() || !levels.getLast().equals(Rational.ONE)) {
      throw new IllegalArgumentException("A level grid must be nonempty and end at one.");
    }
    Rational previous = Rational.ZERO;
    for (Rational level : levels) {
      if (level.compareTo(previous) <= 0 || level.compareTo(Rational.ONE) > 0) {
        throw new IllegalArgumentException("Alpha levels must increase strictly inside (0,1].");
      }
      previous = level;
    }
    return levels;
  }
}
