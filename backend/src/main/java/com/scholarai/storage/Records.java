package com.scholarai.storage;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import java.time.Instant;
import java.util.*;

/** Small shared helpers for stored records and role checks. */
public final class Records {
  private Records() {}

  public static ObjectNode find(JsonNode rows, String key, String value) {
    for (JsonNode row : rows) if (row.path(key).asText().equals(value)) return (ObjectNode) row;
    return null;
  }

  public static ObjectNode require(JsonNode rows, String key, String value, String message) {
    var row = find(rows, key, value);
    if (row == null) throw new AidException(404, message);
    return row;
  }

  public static void role(Principal user, String... roles) {
    if (!Arrays.asList(roles).contains(user.role()))
      throw new AidException(403, "This account cannot perform this action.");
  }

  public static void own(Principal user, String id) {
    if (!user.id().equals(id)) throw new AidException(403, "You can only access your own records.");
  }

  public static void audit(
      ObjectNode state, Principal user, String action, String entity, String id) {
    ((ArrayNode) state.get("audit"))
        .addObject()
        .put("id", state.path("audit").size() + 1)
        .put("actorEmail", user.name())
        .put("action", action)
        .put("entity", entity)
        .put("entityId", id)
        .put("createdAt", Instant.now().toString());
  }

  public static String text(JsonNode input, String key, int min, int max) {
    if (!input.path(key).isTextual()) throw new AidException(400, "Invalid " + key);
    String value = input.path(key).asText().trim();
    if (value.length() < min || value.length() > max) throw new AidException(400, "Invalid " + key);
    return value;
  }

  public static double number(JsonNode input, String key, double min, double max, boolean integer) {
    JsonNode node = input.path(key);
    double value;
    try {
      value = node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText());
    } catch (Exception e) {
      throw new AidException(400, "Invalid " + key);
    }
    if (!Double.isFinite(value)
        || value < min
        || value > max
        || (integer && value != Math.rint(value))) throw new AidException(400, "Invalid " + key);
    return value;
  }

  public static ArrayNode split(ObjectMapper json, String value) {
    ArrayNode result = json.createArrayNode();
    for (String part : value.split(",")) if (!part.trim().isEmpty()) result.add(part.trim());
    return result;
  }
}
