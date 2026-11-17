package io.github.gbalazsfalvi.fuzzy.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiHttpTest {
  @LocalServerPort private int port;
  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void actualHttpLifecycleUsesExactStringsAndStableErrors() throws Exception {
    assertEquals(200, send("GET", "/api/health", null, null).statusCode());
    assertEquals(
        4,
        JsonParser.parseString(send("GET", "/api/presets", null, null).body())
            .getAsJsonArray()
            .size());
    var accepted = send("POST", "/api/jobs", JobRequestTest.VALID, null);
    assertEquals(202, accepted.statusCode());
    String path = accepted.headers().firstValue("Location").orElseThrow();
    String status = "";
    String body = "";
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < deadline) {
      var fetched = send("GET", path, null, null);
      assertEquals(200, fetched.statusCode(), fetched.body());
      body = fetched.body();
      status = JsonParser.parseString(body).getAsJsonObject().get("status").getAsString();
      if (status.equals("READY") || status.equals("FAILED")) break;
      Thread.sleep(10);
    }
    assertEquals("READY", status, body);
    var result = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("result");
    assertTrue(result.get("worstBound").getAsJsonPrimitive().isString());
    assertTrue(result.get("verified").getAsBoolean());
    assertEquals(
        400,
        send("POST", "/api/jobs", JobRequestTest.VALID.replace("\"0.2\"", "0.2"), null)
            .statusCode());
    assertEquals(400, send("GET", "/api/jobs/not-a-uuid", null, null).statusCode());
    assertEquals(404, send("GET", "/api/jobs/" + UUID.randomUUID(), null, null).statusCode());
  }

  @Test
  void rejectsForeignOriginsAndFetchSitesButAllowsSameOrigin() throws Exception {
    assertEquals(
        403, send("POST", "/api/jobs", JobRequestTest.VALID, "https://example.org").statusCode());
    assertEquals(403, send("DELETE", "/api/jobs/" + UUID.randomUUID(), null, "null").statusCode());
    assertEquals(
        202,
        send("POST", "/api/jobs", JobRequestTest.VALID, "http://127.0.0.1:" + port).statusCode());
    var request =
        HttpRequest.newBuilder(uri("/api/jobs"))
            .header("Content-Type", "application/json")
            .header("Sec-Fetch-Site", "cross-site")
            .POST(HttpRequest.BodyPublishers.ofString(JobRequestTest.VALID))
            .build();
    assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  private HttpResponse<String> send(String method, String path, String body, String origin)
      throws Exception {
    var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
    if (origin != null) builder.header("Origin", origin);
    return client.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
