package io.github.gbalazsfalvi.fuzzy.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobServiceTest {
  @Test
  void realSolverProducesVerifiedStringEvidence() throws Exception {
    try (JobService jobs = new JobService(new ModelRegistry(Path.of("absent-model-directory")))) {
      UUID id = jobs.submit(JobRequest.parse(JobRequestTest.VALID));
      long deadline = System.nanoTime() + 10_000_000_000L;
      while (!jobs.terminal(id) && System.nanoTime() < deadline) Thread.sleep(10);
      var response = jobs.get(id);
      assertEquals("READY", response.get("status"));
      assertNotNull(response.get("result"));
      String json = new com.google.gson.Gson().toJson(response);
      assertTrue(json.contains("\"verified\":true"));
      assertTrue(json.contains("\"worstBound\":\""));
    }
  }

  @Test
  void queuedCancellationNeverPublishesCertificateAndQueueIsBounded() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (JobService jobs =
        new JobService(
            new ModelRegistry(Path.of("absent")),
            () -> {
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            })) {
      var request = JobRequest.parse(JobRequestTest.VALID);
      UUID first = jobs.submit(request);
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      UUID second = jobs.submit(request);
      UUID third = jobs.submit(request);
      assertThrows(JobService.CapacityException.class, () -> jobs.submit(request));
      jobs.cancel(second);
      assertEquals("CANCELLED", jobs.get(second).get("status"));
      assertNull(jobs.get(second).get("result"));
      jobs.cancel(first);
      jobs.cancel(third);
      release.countDown();
      long deadline = System.nanoTime() + 5_000_000_000L;
      while (!jobs.terminal(first) && System.nanoTime() < deadline) Thread.sleep(10);
      assertEquals("CANCELLED", jobs.get(first).get("status"));
      assertNull(jobs.get(first).get("result"));
    } finally {
      release.countDown();
    }
  }

  @Test
  void retainsOnlyTwentyJobsAndEvictsOldestTerminalRecord() throws Exception {
    try (JobService jobs = new JobService(new ModelRegistry(Path.of("absent")))) {
      var request =
          JobRequest.parse(
              JobRequestTest.VALID.replace("\"maxRefinements\":8", "\"maxRefinements\":0"));
      UUID oldest = null;
      for (int index = 0; index < 21; index++) {
        UUID id = jobs.submit(request);
        if (oldest == null) oldest = id;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!jobs.terminal(id) && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(jobs.terminal(id));
      }
      assertEquals(20, jobs.list().size());
      UUID expired = oldest;
      assertThrows(java.util.NoSuchElementException.class, () -> jobs.get(expired));
    }
  }
}
