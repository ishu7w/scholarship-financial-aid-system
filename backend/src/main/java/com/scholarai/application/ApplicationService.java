package com.scholarai.application;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.scholarship.*;
import com.scholarai.scoring.ScoreService;
import com.scholarai.storage.*;
import com.scholarai.student.StudentProfile;
import java.time.*;
import java.util.*;

/** Owns submission, frozen scores, withdrawal and saved programs. */
public final class ApplicationService {
  private final PlatformRepository repository;
  private final ObjectMapper json;
  private final ScoreService scores;
  private final MatchingService matching;

  public ApplicationService(
      PlatformRepository repository,
      ObjectMapper json,
      ScoreService scores,
      MatchingService matching) {
    this.repository = repository;
    this.json = json;
    this.scores = scores;
    this.matching = matching;
  }

  public ArrayNode list(Principal user, String studentId) {
    own(user, studentId);
    ArrayNode result = json.createArrayNode();
    for (JsonNode row : repository.read().path("applications"))
      if (row.path("studentId").asText().equals(studentId)) result.add(row);
    return result;
  }

  public void submit(Principal user, String scholarshipId) {
    role(user, "student");
    repository.update(
        state -> {
          var sch =
              require(state.path("scholarships"), "id", scholarshipId, "Scholarship not found");
          if (!sch.path("status").asText().equals("active"))
            throw new AidException(409, "This scholarship is not accepting applications");
          if (LocalDate.parse(sch.path("deadline").asText().substring(0, 10))
              .atStartOfDay(ZoneOffset.UTC)
              .toInstant()
              .isBefore(Instant.now()))
            throw new AidException(409, "This scholarship's deadline has passed");
          JsonNode profile = state.path("students").path(user.id());
          if (profile.isMissingNode())
            throw new AidException(400, "Complete your profile before applying");
          ArrayNode applications = (ArrayNode) state.get("applications");
          for (JsonNode app : applications)
            if (app.path("studentId").asText().equals(user.id())
                && app.path("scholarshipId").asText().equals(scholarshipId))
              throw new AidException(409, "You have already applied to this scholarship");
          StudentProfile student = json.convertValue(profile, StudentProfile.class);
          ObjectNode publicScholarship = sch.deepCopy();
          publicScholarship.remove(List.of("status", "institutionId"));
          var score = scores.calculate(student);
          var match =
              matching.match(student, json.convertValue(publicScholarship, Scholarship.class));
          ObjectNode app = applications.addObject();
          app.put("id", UUID.randomUUID().toString())
              .put("studentId", user.id())
              .put("scholarshipId", scholarshipId)
              .put("status", "submitted")
              .put("submittedAt", Instant.now().toString())
              .putNull("decidedAt");
          ObjectNode snapshot = app.putObject("aiSnapshot");
          snapshot
              .put("total", score.total())
              .put("confidence", score.confidence())
              .put("matchScore", match.matchScore())
              .put("winProbability", match.winProbability());
          snapshot.set("components", json.valueToTree(score.components()));
          snapshot.set("reasons", json.valueToTree(match.reasons()));
          app.set("rejectionReasons", json.valueToTree(match.missingCriteria()));
          app.put("documentsVerified", 0).put("documentsTotal", 0);
          sch.put("applicants", sch.path("applicants").asInt() + 1);
          audit(state, user, "application.submitted", "application", app.path("id").asText());
          return null;
        });
  }

  public void withdraw(Principal user, String scholarshipId) {
    role(user, "student");
    repository.update(
        state -> {
          ArrayNode applications = (ArrayNode) state.get("applications");
          for (int i = 0; i < applications.size(); i++) {
            JsonNode app = applications.get(i);
            if (!app.path("studentId").asText().equals(user.id())
                || !app.path("scholarshipId").asText().equals(scholarshipId)) continue;
            if (Set.of("approved", "rejected").contains(app.path("status").asText()))
              throw new AidException(409, "A decided application cannot be withdrawn");
            applications.remove(i);
            var sch = find(state.path("scholarships"), "id", scholarshipId);
            if (sch != null) sch.put("applicants", Math.max(0, sch.path("applicants").asInt() - 1));
            audit(state, user, "application.withdrawn", "application", app.path("id").asText());
            return null;
          }
          throw new AidException(404, "No application found");
        });
  }

  public JsonNode saved(Principal user, String studentId) {
    own(user, studentId);
    JsonNode rows = repository.read().path("saved").path(studentId);
    return rows.isArray() ? rows : json.createArrayNode();
  }

  public boolean toggleSave(Principal user, String id) {
    role(user, "student");
    return repository.update(
        state -> {
          require(state.path("scholarships"), "id", id, "Scholarship not found");
          ObjectNode saved = (ObjectNode) state.get("saved");
          ArrayNode ids = saved.withArray(user.id());
          for (int i = 0; i < ids.size(); i++)
            if (ids.get(i).asText().equals(id)) {
              ids.remove(i);
              return false;
            }
          ids.add(id);
          return true;
        });
  }
}
