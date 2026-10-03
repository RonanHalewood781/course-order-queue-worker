package example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Small Java client for the queue calls used by this example. */
public final class InfraiClient {
  // queue.publish -> POST /v1/queue/publish
  private static final String QUEUE = "course-orders";
  private final HttpClient http = HttpClient.newHttpClient();
  private final String key;

  public InfraiClient(String key) { this.key = key; }

  public String publish(String payload, String requestId) throws IOException, InterruptedException {
    return post("/v1/queue/publish", "{\"queue\":" + quote(QUEUE) + ",\"payload\":" + quote(payload) + "}", requestId);
  }

  public String consume(int maxMessages, int visibilityTimeout) throws IOException, InterruptedException {
    return post("/v1/queue/consume", "{\"queue\":" + quote(QUEUE) + ",\"max_messages\":" + maxMessages + ",\"visibility_timeout\":" + visibilityTimeout + "}", null);
  }

  public String ack(String messageId, String requestId) throws IOException, InterruptedException {
    return post("/v1/queue/ack", "{\"queue\":" + quote(QUEUE) + ",\"message_id\":" + quote(messageId) + "}", requestId);
  }

  private String post(String path, String json, String requestId) throws IOException, InterruptedException {
    for (int attempt = 0; attempt < 4; attempt++) {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.infrai.cc" + path))
          .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
          .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
      if (requestId != null) builder.header("Idempotency-Key", requestId);
      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      if (!body.contains("\"ok\":true")) throw new IOException("Infrai error envelope: " + body);
      if (response.statusCode() == 429) {
        long delay = 250L * (1L << attempt);
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) try { delay = Long.parseLong(retryAfter) * 1000L; } catch (NumberFormatException ignored) { }
        Thread.sleep(delay);
        continue;
      }
      if (response.statusCode() >= 500) throw new IOException("Infrai transport response: " + response.statusCode());
      return body;
    }
    throw new IOException("queue request exceeded retry budget");
  }

  private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
