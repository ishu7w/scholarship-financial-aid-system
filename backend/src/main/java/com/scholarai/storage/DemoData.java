package com.scholarai.storage;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;

public final class DemoData {
  private DemoData() {}

  public static ObjectNode load(ObjectMapper json) throws Exception {
    ObjectNode seed =
        (ObjectNode) json.readTree(DemoData.class.getResourceAsStream("/demo-data.json"));
    ObjectNode state = json.createObjectNode();
    state.putObject("students").set(seed.get("student").path("id").asText(), seed.get("student"));
    state.set("scholarships", seed.get("scholarships"));
    for (JsonNode sch : state.path("scholarships"))
      ((ObjectNode) sch).put("status", "active").put("institutionId", "demo-institution");
    ArrayNode applications = state.putArray("applications");
    int i = 0;
    for (JsonNode row : seed.path("applicants")) {
      String id = row.path("student").path("id").asText();
      ((ObjectNode) state.get("students")).set(id, row.get("student"));
      String status =
          switch (row.path("status").asText()) {
            case "approved" -> "approved";
            case "rejected" -> "rejected";
            case "pending" -> "submitted";
            default -> "under_review";
          };
      ObjectNode app = applications.addObject();
      app.put("id", "demo-app-" + i++)
          .put("scholarshipId", row.path("scholarshipId").asText())
          .put("studentId", id)
          .put("status", status);
      app.putNull("aiSnapshot");
      app.putArray("rejectionReasons");
      app.set("submittedAt", row.get("appliedOn"));
      app.putNull("decidedAt");
      app.set("documentsVerified", row.get("documentsVerified"));
      app.set("documentsTotal", row.get("documentsTotal"));
    }
    state.putObject("saved");
    state.putArray("documents");
    state.putArray("notifications");
    state.putArray("audit");
    ArrayNode users = state.putArray("users");
    user(users, "stu-aarya", "Aarya Sharma", "aarya@university.edu", "student");
    user(users, "ins-stateuni", "State University", "admin@stateuni.edu", "institution");
    user(users, "adm-root", "Platform Admin", "admin@scholarai.dev", "admin");
    return state;
  }

  private static void user(ArrayNode users, String id, String name, String email, String role) {
    users
        .addObject()
        .put("id", id)
        .put("name", name)
        .put("email", email)
        .put("role", role)
        .put("disabled", false)
        .put("createdAt", Instant.now().toString());
  }
}
