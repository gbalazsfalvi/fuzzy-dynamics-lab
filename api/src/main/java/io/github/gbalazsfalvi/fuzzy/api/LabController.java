package io.github.gbalazsfalvi.fuzzy.api;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Small stable JSON boundary around finite asynchronous jobs. */
@RestController
@RequestMapping("/api")
final class LabController {
  private final JobService jobs;
  private final ModelRegistry models;

  LabController(JobService jobs, ModelRegistry models) {
    this.jobs = jobs;
    this.models = models;
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP", "service", "fuzzy-dynamics-lab", "mode", "LOCAL_RESEARCH");
  }

  @GetMapping("/presets")
  List<Map<String, Object>> presets() {
    return Presets.descriptions();
  }

  @GetMapping("/models")
  List<Map<String, String>> models() {
    return models.descriptions();
  }

  @GetMapping("/jobs")
  List<Map<String, Object>> jobs() {
    return jobs.list();
  }

  @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> create(HttpServletRequest request) throws IOException {
    byte[] body = request.getInputStream().readNBytes(4097);
    if (body.length > 4096) throw new IllegalArgumentException("JSON body exceeds 4096 bytes.");
    UUID id = jobs.submit(JobRequest.parse(new String(body, StandardCharsets.UTF_8)));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", "/api/jobs/" + id)
        .body(jobs.get(id));
  }

  @GetMapping("/jobs/{id}")
  Map<String, Object> get(@PathVariable("id") UUID id) {
    return jobs.get(id);
  }

  @DeleteMapping("/jobs/{id}")
  Map<String, Object> cancel(@PathVariable("id") UUID id) {
    return jobs.cancel(id);
  }
}
