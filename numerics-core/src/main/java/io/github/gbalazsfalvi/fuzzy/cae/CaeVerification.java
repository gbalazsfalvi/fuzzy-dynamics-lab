package io.github.gbalazsfalvi.fuzzy.cae;

import java.util.List;
import java.util.Objects;

/**
 * Strict canonical certificate recomputation outcome.
 *
 * @param issues the reasons for rejection, empty on success
 * @param work the recomputation calls, separate from solver costs
 */
public record CaeVerification(List<String> issues, CaeWork work) {
  /**
   * Copies verification findings.
   *
   * @param issues the immutable rejection details
   * @param work the verification operation counts
   */
  public CaeVerification {
    issues = List.copyOf(issues);
    Objects.requireNonNull(work, "work");
  }

  /**
   * Indicates whether coverage, context, numerical evidence, and aggregate bounds matched.
   *
   * @return true only after all verification checks passed
   */
  public boolean valid() {
    return issues.isEmpty();
  }
}
