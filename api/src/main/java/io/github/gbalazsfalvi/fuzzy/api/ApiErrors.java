package io.github.gbalazsfalvi.fuzzy.api;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Stable error codes without stack traces or local filesystem details. */
@RestControllerAdvice
final class ApiErrors {
  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, String>> invalid(Exception exception) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_REQUEST",
                "message",
                "Request fields, types, preset, model, or safety budgets are invalid."));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Map<String, String>> missing(NoSuchElementException exception) {
    return ResponseEntity.status(404)
        .body(Map.of("code", "JOB_NOT_FOUND", "message", "Job is unknown or expired."));
  }

  @ExceptionHandler(JobService.CapacityException.class)
  ResponseEntity<Map<String, String>> capacity(JobService.CapacityException exception) {
    return ResponseEntity.status(429)
        .header("Retry-After", "2")
        .body(
            Map.of(
                "code",
                "CAPACITY_REACHED",
                "message",
                "The local numerical worker queue is full. Retry after an active job completes."));
  }
}
