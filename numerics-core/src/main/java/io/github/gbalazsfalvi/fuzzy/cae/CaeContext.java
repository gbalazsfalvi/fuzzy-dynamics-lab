package io.github.gbalazsfalvi.fuzzy.cae;

import java.util.Objects;

/**
 * Mathematical and numerical identity of a certificate.
 *
 * @param methodVersion the exact certificate recipe version
 * @param problemDescription the canonical mathematical problem
 * @param precision the significant decimal precision
 */
public record CaeContext(String methodVersion, String problemDescription, int precision) {
  /** The canonical certificate recipe implemented by milestone 02. */
  public static final String VERSION = "cae-02-v1";

  /**
   * Validates the certificate identity fields.
   *
   * @param methodVersion the nonblank recipe version
   * @param problemDescription the nonblank complete problem description
   * @param precision the positive arithmetic precision
   * @throws IllegalArgumentException if an identity field is blank or precision is invalid
   */
  public CaeContext {
    Objects.requireNonNull(methodVersion, "methodVersion");
    Objects.requireNonNull(problemDescription, "problemDescription");
    if (methodVersion.isBlank() || problemDescription.isBlank() || precision < 1) {
      throw new IllegalArgumentException("A certificate needs a complete numerical identity.");
    }
  }

  /**
   * Binds a certificate to a problem and rounding policy.
   *
   * @param problem the mathematical problem
   * @param settings the execution settings
   * @return the supported canonical context
   */
  public static CaeContext of(CaeProblem problem, CaeSettings settings) {
    return new CaeContext(VERSION, problem.description(), settings.arithmetic().precision());
  }
}
