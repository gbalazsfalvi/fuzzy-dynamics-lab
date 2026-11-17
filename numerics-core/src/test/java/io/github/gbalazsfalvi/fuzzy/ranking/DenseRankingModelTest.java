package io.github.gbalazsfalvi.fuzzy.ranking;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalArithmetic;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import io.github.gbalazsfalvi.fuzzy.cae.CaeCell;
import io.github.gbalazsfalvi.fuzzy.cae.CaeProblem;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSettings;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CertificateVerifier;
import io.github.gbalazsfalvi.fuzzy.geometry.ExactPoint;
import io.github.gbalazsfalvi.fuzzy.maps.AffineContraction;
import io.github.gbalazsfalvi.fuzzy.maps.TentMap;
import io.github.gbalazsfalvi.fuzzy.membership.TentPeak;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DenseRankingModelTest {
  private static final String DIGEST = "1".repeat(64);

  @Test
  void evaluatesAHandCalculatedStandardizedLinearGain() {
    List<List<Double>> weights = new ArrayList<>(Collections.nCopies(16, List.of(0.0)));
    weights.set(0, List.of(2.0));
    DenseRankingModel model =
        model(
            List.of(
                new DenseRankingModel.Layer(
                    weights, List.of(1.0), DenseRankingModel.Activation.IDENTITY)));
    List<Double> features = new ArrayList<>(Collections.nCopies(16, 0.0));
    features.set(0, 5.0);
    // (5 - 1)/2 = 2; 2*2 + 1 = 5; target = 3 + 4*5 = 23.
    assertEquals(23.0, model.predict(features));
    assertThrows(
        UnsupportedOperationException.class, () -> model.layers().getFirst().weights().clear());
  }

  @Test
  void evaluatesTanhAndKeepsInferenceOutsideTheCertificateEngine() {
    List<List<Double>> firstWeights = new ArrayList<>(Collections.nCopies(16, List.of(0.0)));
    firstWeights.set(0, List.of(2.0));
    DenseRankingModel model =
        model(
            List.of(
                new DenseRankingModel.Layer(
                    firstWeights, List.of(0.0), DenseRankingModel.Activation.TANH),
                new DenseRankingModel.Layer(
                    List.of(List.of(0.5)), List.of(-0.25), DenseRankingModel.Activation.IDENTITY)));
    List<Double> features = new ArrayList<>(Collections.nCopies(16, 0.0));
    features.set(0, 3.0);
    assertEquals(3.0 + 4.0 * (0.5 * StrictMath.tanh(2.0) - 0.25), model.predict(features), 1e-14);
    CaeProblem problem = problem();
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(16),
            new BigDecimal("0.1"),
            40,
            41,
            12,
            5,
            CaeSettings.Strategy.RANKED);
    var result = new CaeSolver(model).solve(problem, settings);
    assertTrue(
        new CertificateVerifier().verify(problem, settings, result.best().orElseThrow()).valid());
    assertTrue(model.score(result.last().orElseThrow().cells().getFirst()).signum() >= 0);
  }

  @Test
  void featuresMatchAHandCheckedRootCellAndPreserveTheirOrder() {
    CaeSettings settings =
        new CaeSettings(
            new DecimalArithmetic(16),
            new BigDecimal("0.01"),
            0,
            1,
            8,
            5,
            CaeSettings.Strategy.MAXIMUM_BOUND);
    CaeCell root =
        new CaeSolver().solve(problem(), settings).last().orElseThrow().cells().getFirst();
    List<Double> feature = CellFeatures.of(root);
    assertEquals(16, feature.size());
    assertEquals(1.0, feature.get(0));
    assertEquals(StrictMath.log1p(2), feature.get(1));
    assertEquals(0.0, feature.get(2));
    assertEquals(0.5, feature.get(3));
    assertEquals(1.0, feature.get(4));
    assertEquals(0.0, feature.get(5));
    assertEquals(1.0, feature.get(6));
    assertEquals(1.0, feature.get(7));
    assertEquals(1.0, feature.get(8));
    assertEquals(2L * (3L * 3 + 1), CellFeatures.estimatedChildWork(root));
    CaeProblem constant =
        new CaeProblem(
            new AffineContraction(Rational.ZERO),
            new TentPeak(ExactPoint.of("1/2"), Rational.ONE, Rational.ONE),
            2);
    CaeCell flat =
        new CaeSolver().solve(constant, settings).last().orElseThrow().cells().getFirst();
    assertEquals(0.0, CellFeatures.of(flat).get(14));
  }

  @Test
  void rejectsMalformedScalingWeightsFeaturesAndNonfiniteInference() {
    DenseRankingModel valid = model(List.of(identityLayer()));
    assertThrows(IllegalArgumentException.class, () -> valid.predict(List.of(1.0)));
    List<Double> invalidFeatures = new ArrayList<>(Collections.nCopies(16, 0.0));
    invalidFeatures.set(2, Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> valid.predict(invalidFeatures));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(), List.of(0.0), DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(List.of(0.0)), List.of(), DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(List.of(0.0)),
                Collections.nCopies(65, 0.0),
                DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(List.of(0.0, 1.0)), List.of(0.0), DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(List.of(Double.POSITIVE_INFINITY)),
                List.of(0.0),
                DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DenseRankingModel.Layer(
                List.of(List.of(0.0)), List.of(Double.NaN), DenseRankingModel.Activation.IDENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            model(
                List.of(
                    new DenseRankingModel.Layer(
                        List.of(List.of(1.0)),
                        List.of(0.0),
                        DenseRankingModel.Activation.IDENTITY))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            model(
                List.of(
                    new DenseRankingModel.Layer(
                        Collections.nCopies(16, List.of(0.0, 0.0)),
                        List.of(0.0, 0.0),
                        DenseRankingModel.Activation.IDENTITY))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            model(
                List.of(
                    new DenseRankingModel.Layer(
                        Collections.nCopies(16, List.of(0.0)),
                        List.of(0.0),
                        DenseRankingModel.Activation.TANH))));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, List.of(0.0), valid.scale(), 1, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), List.of(1.0), 1, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, Collections.nCopies(16, Double.NaN), valid.scale(), 1, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), Collections.nCopies(16, 0.0), 1, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), Collections.nCopies(16, Double.NaN), 1, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), valid.scale(), 0, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), valid.scale(), Double.NaN, DIGEST, DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), valid.scale(), 1, "bad", DIGEST));
    assertThrows(
        IllegalArgumentException.class,
        () -> copy(valid, valid.mean(), valid.scale(), 1, DIGEST, "bad"));
    DenseRankingModel overflow =
        model(
            List.of(
                new DenseRankingModel.Layer(
                    Collections.nCopies(16, List.of(Double.MAX_VALUE)),
                    List.of(Double.MAX_VALUE),
                    DenseRankingModel.Activation.IDENTITY)));
    assertThrows(
        ArithmeticException.class,
        () -> overflow.predict(Collections.nCopies(16, Double.MAX_VALUE)));
  }

  private static CaeProblem problem() {
    return new CaeProblem(
        new TentMap(), new TentPeak(ExactPoint.of("1/2"), Rational.of(1, 4), Rational.ONE), 2);
  }

  private static DenseRankingModel.Layer identityLayer() {
    return new DenseRankingModel.Layer(
        Collections.nCopies(16, List.of(0.0)), List.of(0.0), DenseRankingModel.Activation.IDENTITY);
  }

  private static DenseRankingModel model(List<DenseRankingModel.Layer> layers) {
    return new DenseRankingModel(
        "test-model",
        CellFeatures.VERSION,
        CellFeatures.names(),
        Collections.nCopies(16, 1.0),
        Collections.nCopies(16, 2.0),
        3.0,
        4.0,
        layers,
        DIGEST,
        DIGEST,
        0);
  }

  private static DenseRankingModel copy(
      DenseRankingModel model,
      List<Double> mean,
      List<Double> scale,
      double targetScale,
      String dataHash,
      String codeHash) {
    return new DenseRankingModel(
        model.id(),
        model.featureVersion(),
        model.featureNames(),
        mean,
        scale,
        model.targetMean(),
        targetScale,
        model.layers(),
        dataHash,
        codeHash,
        model.seed());
  }
}
