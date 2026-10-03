package com.scholarai.aid.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.PlatformRepository;
import java.util.*;

/** Financial aid uses the same hosted JSON storage as the scholarship modules. */
public final class SharedAidRepository implements AidRepository {
  private final PlatformRepository repository;
  private final ObjectMapper json;
  public SharedAidRepository(PlatformRepository repository, ObjectMapper json) { this.repository = repository; this.json = json; }
  @Override public List<AidRecord> list(String studentId) {
    List<AidRecord> result = new ArrayList<>();
    for (var row : repository.read().path("applications")) {
      AidRecord record = json.convertValue(row, AidRecord.class);
      if (studentId == null || record.studentId().equals(studentId)) result.add(record);
    }
    return List.copyOf(result);
  }
  @Override public Optional<AidRecord> find(String id) { return list(null).stream().filter(row -> row.id().equals(id)).findFirst(); }
  @Override public void create(AidRecord record) {
    repository.update(state -> {
      ArrayNode rows = (ArrayNode) state.get("applications");
      for (var row : rows)
        if (row.path("studentId").asText().equals(record.studentId()) && row.path("programId").asText().equals(record.programId()))
          throw new AidException(409, "You have already applied to this program.");
      rows.add(json.valueToTree(record)); return null;
    });
  }
  @Override public void update(AidRecord next, long expectedVersion) {
    repository.update(state -> {
      ArrayNode rows = (ArrayNode) state.get("applications");
      for (int i = 0; i < rows.size(); i++) if (rows.get(i).path("id").asText().equals(next.id())) {
        if (rows.get(i).path("version").asLong() != expectedVersion) throw new AidException(409, "Another reviewer changed this application. Refresh and try again.");
        rows.set(i, json.valueToTree(next)); return null;
      }
      throw new AidException(404, "Application not found.");
    });
  }
}
