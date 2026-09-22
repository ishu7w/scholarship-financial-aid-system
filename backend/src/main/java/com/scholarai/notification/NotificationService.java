package com.scholarai.notification;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.Principal;
import com.scholarai.storage.PlatformRepository;
import java.util.*;

public final class NotificationService {
  private final PlatformRepository repository;
  private final ObjectMapper json;

  public NotificationService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public ObjectNode feed(Principal user) {
    List<JsonNode> rows = new ArrayList<>();
    int unread = 0;
    for (JsonNode row : repository.read().path("notifications"))
      if (row.path("studentId").asText().equals(user.id())) {
        rows.add(row);
        if (!row.path("read").asBoolean()) unread++;
      }
    Collections.reverse(rows);
    ObjectNode result = json.createObjectNode().put("unread", unread);
    result.set("items", json.valueToTree(rows.stream().limit(12).toList()));
    return result;
  }

  public ObjectNode markRead(Principal user, String id) {
    repository.update(
        state -> {
          for (JsonNode row : state.path("notifications"))
            if (row.path("studentId").asText().equals(user.id())
                && (id == null || row.path("id").asText().equals(id)))
              ((ObjectNode) row).put("read", true);
          return null;
        });
    return feed(user);
  }
}
