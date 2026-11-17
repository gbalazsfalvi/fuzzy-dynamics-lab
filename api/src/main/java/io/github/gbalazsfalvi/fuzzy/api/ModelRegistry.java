package io.github.gbalazsfalvi.fuzzy.api;

import com.google.gson.Gson;
import io.github.gbalazsfalvi.fuzzy.ranking.DenseRankingModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Startup-only allowlist of bounded exported models; client identifiers never become paths. */
final class ModelRegistry {
  private final Map<String, DenseRankingModel> models;

  ModelRegistry(Path directory) throws IOException {
    Map<String, DenseRankingModel> loaded = new LinkedHashMap<>();
    if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      try (var files = Files.list(directory)) {
        for (Path file :
            files
                .filter(path -> path.getFileName().toString().matches("[a-z0-9-]+\\.json"))
                .sorted()
                .limit(32)
                .toList()) {
          if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1_048_576)
            continue;
          try {
            DenseRankingModel model =
                new Gson().fromJson(Files.readString(file), DenseRankingModel.class);
            if (model != null && !loaded.containsKey(model.id())) loaded.put(model.id(), model);
          } catch (RuntimeException exception) {
            // Invalid artifacts are unavailable, never partially trusted inference models.
          }
        }
      }
    }
    models = Map.copyOf(loaded);
  }

  DenseRankingModel require(String id) {
    DenseRankingModel model = models.get(id);
    if (model == null) throw new IllegalArgumentException("Requested modelId is unavailable.");
    return model;
  }

  List<Map<String, String>> descriptions() {
    return models.values().stream()
        .sorted(java.util.Comparator.comparing(DenseRankingModel::id))
        .map(model -> Map.of("id", model.id(), "featureVersion", model.featureVersion()))
        .toList();
  }
}
