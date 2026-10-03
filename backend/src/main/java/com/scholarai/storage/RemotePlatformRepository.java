package com.scholarai.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scholarai.aid.domain.AidException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Function;

/** Shared hosted storage; services keep the same simple repository interface. */
public final class RemotePlatformRepository implements PlatformRepository {
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final URI endpoint;
  private final String key;
  private final String dataset;
  private final ObjectNode seed;

  public RemotePlatformRepository(ObjectMapper json, String endpoint, String key, String dataset, ObjectNode seed) {
    this.json = json;
    this.endpoint = URI.create(endpoint);
    if (!this.endpoint.getScheme().equals("https") && !this.endpoint.getHost().equals("127.0.0.1"))
      throw new IllegalArgumentException("Hosted storage requires HTTPS");
    this.key = key;
    this.dataset = dataset;
    this.seed = seed.deepCopy();
  }

  private ObjectNode request(ObjectNode body) {
    try {
      body.put("dataset", dataset);
      HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
          .header("Content-Type", "application/json").header("X-Demo-Storage-Key", key)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 409) return json.createObjectNode().put("conflict", true);
      if (response.statusCode() != 200) throw new AidException(503, "Shared demo storage is unavailable. Please retry.");
      return (ObjectNode) json.readTree(response.body());
    } catch (AidException e) { throw e; }
    catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new AidException(503, "Shared demo storage is unavailable. Please retry.");
    }
  }

  private ObjectNode snapshot() {
    ObjectNode body = json.createObjectNode().put("operation", "read");
    body.set("seed", seed);
    return request(body);
  }

  @Override public ObjectNode read() { return ((ObjectNode) snapshot().get("payload")).deepCopy(); }

  @Override public <T> T update(Function<ObjectNode, T> operation) {
    for (int attempt = 0; attempt < 6; attempt++) {
      ObjectNode snapshot = snapshot();
      ObjectNode state = ((ObjectNode) snapshot.get("payload")).deepCopy();
      T result = operation.apply(state);
      ObjectNode body = json.createObjectNode().put("operation", "write").put("version", snapshot.path("version").asLong());
      body.set("payload", state);
      if (!request(body).path("conflict").asBoolean()) return result;
    }
    throw new AidException(409, "Another demo user changed this data. Please retry.");
  }
}
