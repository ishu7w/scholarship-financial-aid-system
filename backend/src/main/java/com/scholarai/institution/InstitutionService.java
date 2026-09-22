package com.scholarai.institution;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class InstitutionService {
  private final PlatformRepository repository;
  private final ObjectMapper json;

  public InstitutionService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public String institutionId(Principal user) {
    if (user.demo()) return "demo-institution";
    role(user, "institution", "admin");
    return "institution-" + user.id();
  }

  public ObjectNode institution(Principal user, String profileId) {
    own(user, profileId);
    return json.createObjectNode()
        .put("id", institutionId(user))
        .put("profileId", user.id())
        .put("orgName", user.demo() ? "State University" : user.name())
        .put("verified", user.demo());
  }

  private void check(Principal user, String id) {
    if (!institutionId(user).equals(id))
      throw new AidException(403, "This institution does not belong to your account");
  }

  public ArrayNode scholarships(Principal user, String institutionId) {
    check(user, institutionId);
    ArrayNode result = json.createArrayNode();
    for (JsonNode sch : repository.read().path("scholarships"))
      if (sch.path("institutionId").asText().equals(institutionId)) result.add(sch);
    return result;
  }

  public ArrayNode applicants(Principal user, String institutionId) {
    check(user, institutionId);
    ObjectNode state = repository.read();
    ArrayNode result = json.createArrayNode();
    for (JsonNode app : state.path("applications")) {
      var sch = find(state.path("scholarships"), "id", app.path("scholarshipId").asText());
      if (sch == null || !sch.path("institutionId").asText().equals(institutionId)) continue;
      ObjectNode row = ((ObjectNode) app).deepCopy();
      row.put("applicationId", app.path("id").asText());
      row.put("scholarshipName", sch.path("name").asText())
          .put("scholarshipCategory", sch.path("category").asText());
      row.set("student", state.path("students").path(app.path("studentId").asText()));
      int documentCount = 0, verifiedCount = 0;
      for (JsonNode document : state.path("documents")) {
        if (document.path("studentId").asText().equals(app.path("studentId").asText())) {
          documentCount++;
          if (document.path("verificationStatus").asText().equals("verified")) verifiedCount++;
        }
      }
      if (documentCount > 0 || !app.path("id").asText().startsWith("demo-app-")) {
        row.put("documentsTotal", documentCount).put("documentsVerified", verifiedCount);
      }
      result.add(row);
    }
    return result;
  }

  /** Stored application snapshots remain the basis for ranking after profile edits. */
  public ArrayNode rankedApplicants(Principal user, String institutionId) {
    var scores = new com.scholarai.scoring.ScoreService();
    var fraud = new com.scholarai.scoring.FraudService();
    List<ObjectNode> ranked = new ArrayList<>();
    for (JsonNode applicant : applicants(user, institutionId)) {
      ObjectNode row = ((ObjectNode) applicant).deepCopy();
      var profile =
          json.convertValue(row.path("student"), com.scholarai.student.StudentProfile.class);
      JsonNode snapshot = row.path("aiSnapshot");
      if (snapshot.isNull()) {
        long total = scores.calculate(profile).total();
        row.put("aiTotal", total).put("matchScore", total).put("frozen", false);
      } else {
        row.set("aiTotal", snapshot.path("total"));
        row.set("matchScore", snapshot.path("matchScore"));
        row.put("frozen", true);
      }
      row.set("fraud", json.valueToTree(fraud.assess(profile)));
      row.remove(List.of("id", "studentId", "scholarshipCategory", "aiSnapshot", "decidedAt"));
      ranked.add(row);
    }
    ranked.sort(
        Comparator.comparingInt((ObjectNode row) -> row.path("matchScore").asInt()).reversed());
    return json.valueToTree(ranked);
  }

  public ObjectNode aggregates(Principal user, String institutionId) {
    return aggregate(applicants(user, institutionId));
  }

  public ObjectNode aggregate(ArrayNode rows) {
    Map<String, int[]> monthly = new TreeMap<>(), regions = new LinkedHashMap<>();
    Map<String, Integer> categories = new LinkedHashMap<>();
    for (JsonNode row : rows) {
      int approved = row.path("status").asText().equals("approved") ? 1 : 0;
      if (!row.path("submittedAt").isNull()) {
        String key = row.path("submittedAt").asText().substring(0, 7);
        int[] count = monthly.computeIfAbsent(key, k -> new int[2]);
        count[0]++;
        count[1] += approved;
      }
      String category = row.path("scholarshipCategory").asText();
      if (!category.isBlank()) categories.merge(category, 1, Integer::sum);
      String region = row.path("student").path("location").asText();
      if (!region.isBlank()) {
        int[] count = regions.computeIfAbsent(region, k -> new int[2]);
        count[0]++;
        count[1] += approved;
      }
    }
    ObjectNode result = json.createObjectNode();
    ArrayNode months = result.putArray("monthly"),
        cats = result.putArray("categories"),
        places = result.putArray("regions");
    monthly.entrySet().stream()
        .skip(Math.max(0, monthly.size() - 12))
        .forEach(
            e ->
                months
                    .addObject()
                    .put(
                        "month",
                        YearMonth.parse(e.getKey())
                            .format(DateTimeFormatter.ofPattern("MMM yy", Locale.US)))
                    .put("applications", e.getValue()[0])
                    .put("approvals", e.getValue()[1]));
    categories.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .forEach(e -> cats.addObject().put("name", e.getKey()).put("value", e.getValue()));
    regions.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
        .forEach(
            e ->
                places
                    .addObject()
                    .put("region", e.getKey())
                    .put("students", e.getValue()[0])
                    .put("funded", e.getValue()[1]));
    return result;
  }

  public String save(Principal user, String id, JsonNode input) {
    role(user, "institution", "admin");
    String institution = institutionId(user);
    return repository.update(
        state -> {
          ObjectNode record =
              id == null
                  ? json.createObjectNode()
                  : require(state.path("scholarships"), "id", id, "Scholarship not found");
          if (id != null && !record.path("institutionId").asText().equals(institution))
            throw new AidException(403, "This program does not belong to your institution");
          String name = text(input, "name", 3, 140);
          String key = id == null ? "sch-" + UUID.randomUUID() : id;
          record
              .put("id", key)
              .put("institutionId", institution)
              .put("name", name)
              .put("provider", text(input, "provider", 2, 140));
          String category = text(input, "category", 1, 40);
          if (!Set.of(
                  "Government",
                  "Private",
                  "NGO",
                  "University",
                  "International",
                  "Corporate",
                  "Research Grant",
                  "Need-based",
                  "Merit",
                  "Women",
                  "Minority",
                  "Sports")
              .contains(category)) throw new AidException(400, "Invalid category");
          record
              .put("category", category)
              .put("amount", number(input, "amount", 0, 100000000, true))
              .put("currency", text(input, "currency", 3, 3));
          String deadline = text(input, "deadline", 10, 10);
          LocalDate.parse(deadline);
          record.put("deadline", deadline);
          record
              .put("seats", number(input, "seats", 1, 1000000, true))
              .put("description", text(input, "description", 20, 20000));
          record.set("tags", split(json, input.path("tags").asText("")));
          String status = input.path("status").asText("active");
          if (!Set.of("draft", "active", "closed").contains(status))
            throw new AidException(400, "Invalid status");
          record.put("status", status);
          if (id == null) record.put("applicants", 0);
          ObjectNode criteria = record.putObject("criteria");
          criteria
              .put("minCgpa", number(input, "minCgpa", 0, 10, false))
              .put("minAttendance", number(input, "minAttendance", 0, 100, false));
          if (input.path("maxIncome").isNull() || input.path("maxIncome").asText().isBlank())
            criteria.putNull("maxIncome");
          else criteria.put("maxIncome", number(input, "maxIncome", 0, 100000000, false));
          for (String field :
              List.of(
                  "requiresResearch",
                  "requiresLeadership",
                  "womenOnly",
                  "minorityOnly",
                  "sportsRequired",
                  "disabilityPreferred")) {
            if (!input.path(field).isBoolean()) throw new AidException(400, "Invalid " + field);
            criteria.set(field, input.get(field));
          }
          criteria.set("locations", split(json, input.path("locations").asText("")));
          criteria.set("fields", split(json, input.path("fields").asText("")));
          if (id == null) ((ArrayNode) state.get("scholarships")).add(record);
          audit(
              state,
              user,
              id == null ? "scholarship.created" : "scholarship.updated",
              "scholarship",
              key);
          return key;
        });
  }

  public void decide(Principal user, JsonNode input) {
    role(user, "institution", "admin");
    String institution = institutionId(user);
    String decision = text(input, "decision", 1, 10);
    if (!Set.of("approve", "reject").contains(decision))
      throw new AidException(400, "Invalid decision");
    repository.update(
        state -> {
          var app =
              require(
                  state.path("applications"),
                  "id",
                  text(input, "applicationId", 1, 120),
                  "Application not found");
          var sch =
              require(
                  state.path("scholarships"),
                  "id",
                  app.path("scholarshipId").asText(),
                  "Scholarship not found");
          if (!sch.path("institutionId").asText().equals(institution))
            throw new AidException(403, "This application does not belong to your institution");
          if (!Set.of("submitted", "under_review").contains(app.path("status").asText()))
            throw new AidException(409, "This application has already been decided");
          if (decision.equals("approve")) {
            app.putArray("rejectionReasons");
          } else if (app.path("aiSnapshot").isNull() && app.path("rejectionReasons").isEmpty()) {
            ObjectNode scholarship = sch.deepCopy();
            scholarship.remove(List.of("institutionId", "status"));
            var profile =
                json.convertValue(
                    state.path("students").path(app.path("studentId").asText()),
                    com.scholarai.student.StudentProfile.class);
            var match =
                new com.scholarai.scholarship.MatchingService(
                        new com.scholarai.scoring.ScoreService())
                    .match(
                        profile,
                        json.convertValue(
                            scholarship, com.scholarai.scholarship.Scholarship.class));
            app.set("rejectionReasons", json.valueToTree(match.missingCriteria()));
          }
          String status = decision.equals("approve") ? "approved" : "rejected";
          app.put("status", status).put("decidedAt", Instant.now().toString());
          audit(state, user, "application." + status, "application", app.path("id").asText());
          ((ArrayNode) state.get("notifications"))
              .addObject()
              .put("id", UUID.randomUUID().toString())
              .put("studentId", app.path("studentId").asText())
              .put("type", "application." + status)
              .put("title", "Application " + status)
              .put("body", sch.path("name").asText())
              .put("href", "/scholarships/" + sch.path("id").asText())
              .put("read", false)
              .put("createdAt", Instant.now().toString());
          return null;
        });
  }
}
