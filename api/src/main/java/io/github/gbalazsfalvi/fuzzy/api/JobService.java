package io.github.gbalazsfalvi.fuzzy.api;

import io.github.gbalazsfalvi.fuzzy.cae.CaeResult;
import io.github.gbalazsfalvi.fuzzy.cae.CaeSolver;
import io.github.gbalazsfalvi.fuzzy.cae.CertificateVerifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One numerical worker, two waiting requests, and at most twenty retained job records. */
final class JobService implements AutoCloseable {
  private final ModelRegistry models;
  private final Runnable beforeSolve;
  private final LinkedHashMap<UUID, Job> jobs = new LinkedHashMap<>();
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(2),
          runnable -> {
            Thread worker = new Thread(runnable, "cae-numerical-worker");
            worker.setDaemon(false);
            return worker;
          },
          new ThreadPoolExecutor.AbortPolicy());
  private boolean closed;

  JobService(ModelRegistry models) {
    this(models, () -> {});
  }

  JobService(ModelRegistry models, Runnable beforeSolve) {
    this.models = models;
    this.beforeSolve = beforeSolve;
  }

  synchronized UUID submit(JobRequest request) {
    if (closed) throw new CapacityException();
    if (request.modelId() != null) models.require(request.modelId());
    while (jobs.size() >= 20) {
      UUID oldest =
          jobs.values().stream().filter(Job::terminal).map(job -> job.id).findFirst().orElse(null);
      if (oldest == null) throw new CapacityException();
      jobs.remove(oldest);
    }
    Job job = new Job(request);
    jobs.put(job.id, job);
    try {
      executor.execute(job);
    } catch (RejectedExecutionException exception) {
      jobs.remove(job.id);
      throw new CapacityException();
    }
    return job.id;
  }

  synchronized Map<String, Object> get(UUID id) {
    return require(id).view();
  }

  synchronized boolean terminal(UUID id) {
    return require(id).terminal();
  }

  synchronized List<Map<String, Object>> list() {
    return new ArrayList<>(jobs.values()).reversed().stream().map(Job::view).toList();
  }

  synchronized Map<String, Object> cancel(UUID id) {
    Job job = require(id);
    synchronized (job) {
      if (!job.terminal()) {
        job.cancelled.set(true);
        if (executor.remove(job)) {
          job.status = "CANCELLED";
          job.reason = "CANCEL_REQUESTED";
        } else job.status = "CANCELLING";
      }
      return job.view();
    }
  }

  private Job require(UUID id) {
    Job job = jobs.get(id);
    if (job == null) throw new NoSuchElementException("Job is unknown or expired.");
    return job;
  }

  @Override
  public void close() {
    synchronized (this) {
      closed = true;
      for (UUID id : List.copyOf(jobs.keySet())) cancel(id);
      executor.shutdownNow();
    }
    try {
      executor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  /** Signals immediate overload without retaining a rejected request. */
  static final class CapacityException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private final class Job implements Runnable {
    private final UUID id = UUID.randomUUID();
    private final JobRequest request;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private String status = "QUEUED";
    private String reason;
    private Map<String, Object> result;
    private long start;
    private long elapsed;

    Job(JobRequest request) {
      this.request = request;
    }

    synchronized boolean terminal() {
      return List.of("READY", "CANCELLED", "FAILED").contains(status);
    }

    synchronized Map<String, Object> view() {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", id.toString());
      view.put("status", status);
      view.put("reason", reason);
      view.put("requested", request);
      Map<String, Object> problem =
          new LinkedHashMap<>(Presets.get(request.presetId()).description());
      problem.put("horizon", request.horizon());
      view.put("problem", problem);
      view.put("result", result);
      view.put(
          "elapsedNanos",
          Long.toString(start == 0 ? 0 : terminal() ? elapsed : System.nanoTime() - start));
      return view;
    }

    @Override
    public void run() {
      synchronized (this) {
        if (cancelled.get()) {
          status = "CANCELLED";
          reason = "CANCEL_REQUESTED";
          return;
        }
        status = "RUNNING";
        start = System.nanoTime();
      }
      try {
        beforeSolve.run();
        var problem = Presets.get(request.presetId()).problem(request.horizon());
        var settings = request.settings();
        CaeSolver solver =
            request.modelId() == null
                ? new CaeSolver()
                : new CaeSolver(models.require(request.modelId()));
        CaeResult solved =
            solver.solve(
                problem, settings, () -> cancelled.get() || Thread.currentThread().isInterrupted());
        long solveElapsed = System.nanoTime() - start;
        long verificationStart = System.nanoTime();
        if (!cancelled.get() && solved.status() != CaeResult.Status.CANCELLED) {
          CertificateVerifier verifier = new CertificateVerifier();
          if (solved.best().isPresent()
              && !verifier.verify(problem, settings, solved.best().orElseThrow()).valid())
            throw new IllegalStateException("Best snapshot verification failed.");
          if (!cancelled.get()
              && solved.last().isPresent()
              && !verifier.verify(problem, settings, solved.last().orElseThrow()).valid())
            throw new IllegalStateException("Last snapshot verification failed.");
        }
        synchronized (this) {
          elapsed = System.nanoTime() - start;
          if (cancelled.get() || solved.status() == CaeResult.Status.CANCELLED) {
            status = "CANCELLED";
            reason = "CANCEL_REQUESTED";
          } else {
            result =
                EvidenceDto.result(solved, solveElapsed, System.nanoTime() - verificationStart);
            status = "READY";
            reason = solved.reason().name();
          }
        }
      } catch (RuntimeException exception) {
        synchronized (this) {
          elapsed = System.nanoTime() - start;
          status = cancelled.get() ? "CANCELLED" : "FAILED";
          reason = cancelled.get() ? "CANCEL_REQUESTED" : "EXECUTION_FAILED";
        }
      }
    }
  }
}
