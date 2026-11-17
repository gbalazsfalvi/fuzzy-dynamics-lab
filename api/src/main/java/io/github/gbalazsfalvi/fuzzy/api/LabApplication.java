package io.github.gbalazsfalvi.fuzzy.api;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Local-only research server; model ranking never replaces numerical verification. */
@SpringBootApplication
public class LabApplication {
  /** Creates the Spring application configuration. */
  public LabApplication() {}

  /**
   * Starts the loopback workbench.
   *
   * @param args local Spring configuration arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(LabApplication.class, args);
  }

  @Bean
  ModelRegistry modelRegistry(Environment environment) throws IOException {
    if (!"127.0.0.1".equals(environment.getProperty("server.address", "127.0.0.1")))
      throw new IllegalArgumentException(
          "This unauthenticated research API must bind to 127.0.0.1.");
    return new ModelRegistry(
        Path.of(environment.getProperty("lab.models-directory", "learning/models")));
  }

  @Bean(destroyMethod = "close")
  JobService jobService(ModelRegistry models) {
    return new JobService(models);
  }
}
