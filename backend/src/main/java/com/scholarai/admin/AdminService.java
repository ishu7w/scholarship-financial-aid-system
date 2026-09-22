package com.scholarai.admin;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.*;
import java.util.*;

public final class AdminService {
  private final PlatformRepository repository;
  private final ObjectMapper json;

  public AdminService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  private void check(Principal user) {
    if (!user.demo()) role(user, "admin");
  }

  public ObjectNode users(Principal user) {
    check(user);
    ObjectNode result = json.createObjectNode().put("mode", user.demo() ? "demo" : "live");
    result.set("users", repository.read().get("users"));
    return result;
  }

  public ObjectNode auditPage(Principal user, JsonNode input) {
    check(user);
    int page = (int) number(input, "page", 1, 10000, true);
    String action = input.path("action").asText("");
    List<JsonNode> entries = new ArrayList<>();
    TreeSet<String> actions = new TreeSet<>();
    for (JsonNode row : repository.read().path("audit")) {
      actions.add(row.path("action").asText());
      if (action.isEmpty() || action.equals(row.path("action").asText())) entries.add(row);
    }
    Collections.reverse(entries);
    ObjectNode result =
        json.createObjectNode()
            .put("page", page)
            .put("pageSize", 25)
            .put("total", entries.size())
            .put("mode", user.demo() ? "demo" : "live");
    result.set(
        "entries", json.valueToTree(entries.stream().skip((page - 1L) * 25).limit(25).toList()));
    result.set("actions", json.valueToTree(actions));
    return result;
  }

  public ObjectNode stats(Principal user) {
    check(user);
    ObjectNode state = repository.read();
    ObjectNode result = json.createObjectNode().put("mode", user.demo() ? "demo" : "live");
    ObjectNode roles = result.putObject("usersByRole");
    for (String role : List.of("student", "institution", "admin")) roles.put(role, 0);
    int disabled = 0;
    for (JsonNode row : state.path("users")) {
      String role = row.path("role").asText();
      roles.put(role, roles.path(role).asInt() + 1);
      if (row.path("disabled").asBoolean()) disabled++;
    }
    result.put("totalUsers", state.path("users").size()).put("disabledUsers", disabled);
    ObjectNode scholarships = result.putObject("scholarshipsByStatus");
    for (String s : List.of("draft", "active", "closed")) scholarships.put(s, 0);
    for (JsonNode row : state.path("scholarships")) {
      String s = row.path("status").asText();
      scholarships.put(s, scholarships.path(s).asInt() + 1);
    }
    result.put("totalScholarships", state.path("scholarships").size());
    ObjectNode applications = result.putObject("applicationsByStatus");
    for (String s : List.of("draft", "submitted", "under_review", "approved", "rejected"))
      applications.put(s, 0);
    Map<String, int[]> months = new TreeMap<>();
    for (JsonNode row : state.path("applications")) {
      String s = row.path("status").asText();
      applications.put(s, applications.path(s).asInt() + 1);
      String date = row.path("submittedAt").asText();
      if (date.length() >= 7) {
        int[] counts = months.computeIfAbsent(date.substring(0, 7), k -> new int[2]);
        counts[0]++;
        if (s.equals("approved")) counts[1]++;
      }
    }
    int approved = applications.path("approved").asInt(),
        decided = approved + applications.path("rejected").asInt();
    result
        .put("totalApplications", state.path("applications").size())
        .put("decidedApplications", decided);
    if (decided == 0) result.putNull("approvalRate");
    else result.put("approvalRate", Math.round(approved * 100.0 / decided));
    ArrayNode monthly = result.putArray("monthly");
    months.entrySet().stream()
        .skip(Math.max(0, months.size() - 12))
        .forEach(
            e ->
                monthly
                    .addObject()
                    .put("month", e.getKey())
                    .put("applications", e.getValue()[0])
                    .put("approvals", e.getValue()[1]));
    return result;
  }

  public void updateUser(Principal user, JsonNode input, boolean changeRole) {
    role(user, "admin");
    String id = text(input, "userId", 1, 200);
    repository.update(
        state -> {
          var target = require(state.path("users"), "id", id, "User not found");
          if (changeRole) {
            String role = text(input, "role", 1, 20);
            if (!Set.of("student", "institution", "admin").contains(role))
              throw new AidException(400, "Invalid role");
            if (user.id().equals(id) && !role.equals("admin"))
              throw new AidException(400, "You cannot remove your own admin role");
            target.put("role", role);
          } else {
            if (!input.path("disabled").isBoolean())
              throw new AidException(400, "Invalid disabled flag");
            if (user.id().equals(id) && input.path("disabled").asBoolean())
              throw new AidException(400, "You cannot disable your own account");
            target.set("disabled", input.get("disabled"));
          }
          audit(
              state, user, changeRole ? "user.role_changed" : "user.disabled_changed", "user", id);
          return null;
        });
  }
}
