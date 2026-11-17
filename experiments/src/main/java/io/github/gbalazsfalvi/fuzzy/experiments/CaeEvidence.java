package io.github.gbalazsfalvi.fuzzy.experiments;

import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSnapshot;
import io.github.gbalazsfalvi.fuzzy.cae.CaeWork;
import io.github.gbalazsfalvi.fuzzy.cae.RefinementEvent;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.CertifiedMap;
import io.github.gbalazsfalvi.fuzzy.maps.CoupledTentMap;
import io.github.gbalazsfalvi.fuzzy.maps.LogisticMap;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.MultiPeakMembership;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit JSON schema, with a closed map registry and exact rational model parameters.
 *
 * <p>No runtime class names or polymorphic deserialization are accepted from a report. Core result
 * optionals are represented explicitly as nullable snapshots rather than reflecting into JDK types.
 */
final class CaeEvidence {
  static final String SCHEMA = "cae-evidence-v1";
  static final String SCOPE =
      "Deterministic finite horizon CAE examples; not a novelty, speedup, or infinite horizon claim.";

  private CaeEvidence() {}

  record Archive(
      String schemaVersion,
      String scope,
      String generatedAtUtc,
      String sourceSha256,
      List<Case> cases) {
    Archive {
      if (!SCHEMA.equals(schemaVersion) || !SCOPE.equals(scope)) {
        throw new IllegalArgumentException("Unsupported CAE evidence schema or scope.");
      }
      Instant.parse(generatedAtUtc);
      if (!sourceSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException(
            "Source provenance must be a SHA-256 hexadecimal digest.");
      }
      cases = List.copyOf(cases);
      if (cases.isEmpty() || cases.stream().map(Case::id).distinct().count() != cases.size()) {
        throw new IllegalArgumentException("Evidence needs nonempty, uniquely named cases.");
      }
    }
  }

  record Case(String id, Problem problem, CaeSettings settings, Outcome outcome) {
    Case {
      if (id == null || !id.matches("[a-z0-9-]+")) {
        throw new IllegalArgumentException("A case needs a stable lower case identifier.");
      }
      Objects.requireNonNull(problem, "problem");
      Objects.requireNonNull(settings, "settings");
      Objects.requireNonNull(outcome, "outcome");
      problem.toCore();
      outcome.toCore();
    }
  }

  enum MapKind {
    AFFINE_CONTRACTION,
    TENT,
    LOGISTIC,
    COUPLED_TENT
  }

  record Problem(MapKind map, String parameter, List<Peak> peaks, int horizon) {
    Problem {
      Objects.requireNonNull(map, "map");
      peaks = List.copyOf(peaks);
      if ((map == MapKind.TENT) != (parameter == null)) {
        throw new IllegalArgumentException("Only the tent map has no model parameter.");
      }
    }

    CaeProblem toCore() {
      CertifiedMap dynamics =
          switch (map) {
            case AFFINE_CONTRACTION -> new AffineContraction(Rational.parse(parameter));
            case TENT -> new TentMap();
            case LOGISTIC -> new LogisticMap(Rational.parse(parameter));
            case COUPLED_TENT -> new CoupledTentMap(Rational.parse(parameter));
          };
      return new CaeProblem(
          dynamics, new MultiPeakMembership(peaks.stream().map(Peak::toCore).toList()), horizon);
    }

    static Problem from(CaeProblem source) {
      MapKind kind =
          switch (source.map()) {
            case AffineContraction ignored -> MapKind.AFFINE_CONTRACTION;
            case TentMap ignored -> MapKind.TENT;
            case LogisticMap ignored -> MapKind.LOGISTIC;
            case CoupledTentMap ignored -> MapKind.COUPLED_TENT;
          };
      String parameter =
          switch (source.map()) {
            case AffineContraction value -> value.factor().toString();
            case TentMap ignored -> null;
            case LogisticMap value -> value.parameter().toString();
            case CoupledTentMap value -> value.coupling().toString();
          };
      List<TentPeak> profiles =
          switch (source.membership()) {
            case TentPeak peak -> List.of(peak);
            case MultiPeakMembership profile -> profile.peaks();
          };
      return new Problem(
          kind, parameter, profiles.stream().map(Peak::from).toList(), source.horizon());
    }
  }

  record Peak(List<String> center, String width, String height) {
    Peak {
      center = List.copyOf(center);
      Objects.requireNonNull(width, "width");
      Objects.requireNonNull(height, "height");
    }

    TentPeak toCore() {
      return new TentPeak(
          new ExactPoint(center.stream().map(Rational::parse).toList()),
          Rational.parse(width),
          Rational.parse(height));
    }

    static Peak from(TentPeak peak) {
      return new Peak(
          peak.center().coordinates().stream().map(Rational::toString).toList(),
          peak.width().toString(),
          peak.height().toString());
    }
  }

  record Outcome(
      CaeResult.Status status,
      CaeResult.StopReason reason,
      String detail,
      CaeSnapshot best,
      CaeSnapshot last,
      List<RefinementEvent> history,
      CaeWork work) {
    Outcome {
      history = List.copyOf(history);
    }

    CaeResult toCore() {
      return new CaeResult(
          status,
          reason,
          detail,
          Optional.ofNullable(best),
          Optional.ofNullable(last),
          history,
          work);
    }

    static Outcome from(CaeResult result) {
      return new Outcome(
          result.status(),
          result.reason(),
          result.detail(),
          result.best().orElse(null),
          result.last().orElse(null),
          result.history(),
          result.work());
    }
  }
}
