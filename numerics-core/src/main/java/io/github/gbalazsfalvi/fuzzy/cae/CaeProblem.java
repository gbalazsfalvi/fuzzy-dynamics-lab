package io.github.gbalazsfalvi.fuzzy.cae;

import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.membership.CertifiedMembership;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A supported unit domain map, initial membership, and fixed finite horizon.
 *
 * @param map the built in continuous unit self map
 * @param membership the initial continuous profile
 * @param horizon the number of transitions, excluding the initial state
 */
public record CaeProblem(CertifiedMap map, CertifiedMembership membership, int horizon) {
  /**
   * Checks supported dimensions and the finite horizon.
   *
   * @param map the map
   * @param membership the equally dimensioned initial profile
   * @param horizon the nonnegative horizon below the integer maximum
   * @throws IllegalArgumentException if dimensions disagree or the horizon is invalid
   */
  public CaeProblem {
    Objects.requireNonNull(map, "map");
    Objects.requireNonNull(membership, "membership");
    if (map.dimension() != membership.dimension() || horizon < 0 || horizon == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("The map, membership, and finite horizon must agree.");
    }
  }

  /**
   * Describes every mathematical parameter canonically, without identity based object strings.
   *
   * @return a stable description used to bind certificates to their problem
   */
  public String description() {
    List<TentPeak> peaks =
        switch (membership) {
          case TentPeak peak -> List.of(peak);
          case MultiPeakMembership profile -> profile.peaks();
        };
    String profiles =
        peaks.stream()
            .map(
                peak ->
                    peak.center().coordinates().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(",", "(", ")"))
                        + ":"
                        + peak.width()
                        + ":"
                        + peak.height())
            .collect(Collectors.joining("|"));
    return "map="
        + map.id()
        + ";dimension="
        + map.dimension()
        + ";horizon="
        + horizon
        + ";peaks="
        + profiles;
  }
}
