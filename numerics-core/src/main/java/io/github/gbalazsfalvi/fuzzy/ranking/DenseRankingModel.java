package io.github.gbalazsfalvi.fuzzy.ranking;

import io.github.gbalazsfalvi.fuzzy.cae.CaeCell;
import io.github.gbalazsfalvi.fuzzy.cae.CellRanker;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Frozen standardized dense regression inference for exported ridge and tanh network models.
 *
 * <p>Every value here influences selection only. The Java interval engine retains sole ownership of
 * all mathematical bounds. The model format has no executable code or arbitrary class names.
 *
 * @param id the stable model identifier
 * @param featureVersion the exact descriptor recipe
 * @param featureNames the complete ordered columns
 * @param mean the training feature means
 * @param scale the positive training feature scales
 * @param targetMean the training gain mean
 * @param targetScale the positive training gain scale
 * @param layers the finite dense weight layers, with one linear output
 * @param trainingDataSha256 the exact training CSV content hash
 * @param trainingCodeSha256 the trainer and dependency input hash
 * @param seed the training seed, also recorded for the deterministic ridge baseline
 */
public record DenseRankingModel(
    String id,
    String featureVersion,
    List<String> featureNames,
    List<Double> mean,
    List<Double> scale,
    double targetMean,
    double targetScale,
    List<Layer> layers,
    String trainingDataSha256,
    String trainingCodeSha256,
    long seed)
    implements CellRanker {
  /**
   * Copies weights and checks the complete feature, shape, and provenance contract.
   *
   * @param id the safe model identifier
   * @param featureVersion the feature recipe
   * @param featureNames the exact ordered names
   * @param mean the finite means
   * @param scale the finite positive scales
   * @param targetMean the finite gain mean
   * @param targetScale the finite positive gain scale
   * @param layers the nonempty bounded dense layers
   * @param trainingDataSha256 the training data digest
   * @param trainingCodeSha256 the training code and lockfile digest
   * @param seed the training seed
   * @throws IllegalArgumentException if provenance, features, or numerical shapes are invalid
   */
  public DenseRankingModel {
    featureNames = List.copyOf(featureNames);
    mean = List.copyOf(mean);
    scale = List.copyOf(scale);
    layers = List.copyOf(layers);
    if (id == null
        || !id.matches("[a-z0-9-]+")
        || !CellFeatures.VERSION.equals(featureVersion)
        || !CellFeatures.names().equals(featureNames)
        || mean.size() != featureNames.size()
        || scale.size() != featureNames.size()
        || !Double.isFinite(targetMean)
        || !Double.isFinite(targetScale)
        || targetScale <= 0
        || layers.isEmpty()
        || layers.size() > 4
        || trainingDataSha256 == null
        || !trainingDataSha256.matches("[0-9a-f]{64}")
        || trainingCodeSha256 == null
        || !trainingCodeSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "Frozen model metadata and feature contract must be valid.");
    }
    if (mean.stream().anyMatch(value -> !Double.isFinite(value))
        || scale.stream().anyMatch(value -> !Double.isFinite(value) || value <= 0)) {
      throw new IllegalArgumentException("Model scaling must be finite with positive scales.");
    }
    int inputs = featureNames.size();
    for (Layer layer : layers) {
      if (layer.weights().size() != inputs) {
        throw new IllegalArgumentException("Dense layer input dimensions do not match.");
      }
      inputs = layer.bias().size();
    }
    if (inputs != 1 || layers.getLast().activation() != Activation.IDENTITY) {
      throw new IllegalArgumentException("Gain regression requires one identity output.");
    }
  }

  /**
   * Runs the frozen scaler and dense network on a descriptor vector.
   *
   * @param features the finite descriptors in the model's exact feature order
   * @return the predicted signed local bound reduction
   * @throws IllegalArgumentException if descriptor shape or values are invalid
   * @throws ArithmeticException if finite inference overflows
   */
  public double predict(List<Double> features) {
    if (features.size() != mean.size()
        || features.stream().anyMatch(value -> !Double.isFinite(value))) {
      throw new IllegalArgumentException(
          "Inference descriptors must match the finite model input.");
    }
    List<Double> values = new ArrayList<>();
    for (int index = 0; index < features.size(); index++) {
      values.add((features.get(index) - mean.get(index)) / scale.get(index));
    }
    for (Layer layer : layers) {
      List<Double> next = new ArrayList<>();
      for (int output = 0; output < layer.bias().size(); output++) {
        double value = layer.bias().get(output);
        for (int input = 0; input < values.size(); input++) {
          value += values.get(input) * layer.weights().get(input).get(output);
        }
        next.add(layer.activation() == Activation.TANH ? StrictMath.tanh(value) : value);
      }
      values = next;
    }
    double prediction = targetMean + targetScale * values.getFirst();
    if (!Double.isFinite(prediction)) {
      throw new ArithmeticException("Frozen gain inference overflowed.");
    }
    return prediction;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal score(CaeCell cell) {
    double gain = Math.max(0, predict(CellFeatures.of(cell)));
    return BigDecimal.valueOf(gain)
        .divide(
            BigDecimal.valueOf(CellFeatures.estimatedChildWork(cell)),
            java.math.MathContext.DECIMAL64);
  }

  /**
   * Dense layer with the input by output weight orientation used by the trainer.
   *
   * @param weights the finite rectangular input by output matrix
   * @param bias the finite output bias
   * @param activation the declared activation
   */
  public record Layer(List<List<Double>> weights, List<Double> bias, Activation activation) {
    /**
     * Copies and checks a bounded finite dense layer.
     *
     * @param weights the nonempty rectangular matrix
     * @param bias the positive width output vector, at most 64 units
     * @param activation the declared activation
     * @throws IllegalArgumentException if shape or numerical values are invalid
     */
    public Layer {
      weights = weights.stream().map(List::copyOf).toList();
      bias = List.copyOf(bias);
      Objects.requireNonNull(activation, "activation");
      int outputs = bias.size();
      if (weights.isEmpty()
          || outputs < 1
          || outputs > 64
          || weights.stream()
              .anyMatch(
                  row ->
                      row.size() != outputs
                          || row.stream().anyMatch(value -> !Double.isFinite(value)))
          || bias.stream().anyMatch(value -> !Double.isFinite(value))) {
        throw new IllegalArgumentException(
            "Dense layers need finite rectangular matrices and bounded widths.");
      }
    }
  }

  /** Activations supported by the exported regression format. */
  public enum Activation {
    /** No output transformation. */
    IDENTITY,
    /** Deterministic hyperbolic tangent. */
    TANH
  }
}
