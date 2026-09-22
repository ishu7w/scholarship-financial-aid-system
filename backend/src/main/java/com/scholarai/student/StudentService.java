package com.scholarai.student;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.*;
import java.util.*;

public final class StudentService {
  private final PlatformRepository repository;
  private final ObjectMapper json;

  public StudentService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public JsonNode get(Principal user, String id) {
    own(user, id);
    return repository.read().path("students").path(id);
  }

  public void save(Principal user, JsonNode input) {
    role(user, "student");
    repository.update(
        state -> {
          JsonNode previous = state.path("students").path(user.id());
          ObjectNode profile =
              previous.isObject() ? ((ObjectNode) previous).deepCopy() : json.createObjectNode();
          profile.put("id", user.id()).put("name", text(input, "name", 2, 200));
          profile.put("field", text(input, "field", 2, 200));
          for (String key : List.of("degree", "location"))
            profile.put(key, input.path(key).asText("").trim());
          profile
              .put("email", previous.path("email").asText(""))
              .put("avatarHue", previous.path("avatarHue").asInt(258));
          profile.put("behaviourScore", previous.path("behaviourScore").asDouble(100));
          profile.put("year", number(input, "year", 1, 8, true));
          profile.put("cgpa", number(input, "cgpa", 0, 10, false));
          profile.put("attendance", number(input, "attendance", 0, 100, false));
          profile.put("familyIncome", number(input, "familyIncome", 0, 1e12, false));
          String gender = text(input, "gender", 1, 10);
          if (!Set.of("female", "male", "other").contains(gender))
            throw new AidException(400, "Invalid gender");
          profile.put("gender", gender);
          for (String key : List.of("minority", "disability", "firstGeneration")) {
            if (!input.path(key).isBoolean()) throw new AidException(400, "Invalid " + key);
            profile.set(key, input.get(key));
          }
          for (String key :
              List.of(
                  "achievements",
                  "researchPapers",
                  "hackathons",
                  "certifications",
                  "leadershipRoles",
                  "projects",
                  "previousScholarships")) profile.put(key, number(input, key, 0, 99, true));
          profile.put("sportsLevel", number(input, "sportsLevel", 0, 3, true));
          profile.put("volunteerHours", number(input, "volunteerHours", 0, 9999, true));
          profile.put("sopQuality", number(input, "sopQuality", 0, 100, true));
          profile.put(
              "recommendationStrength", number(input, "recommendationStrength", 0, 100, true));
          profile.set("skills", split(json, input.path("skills").asText("")));
          int filled = 0;
          for (String key : List.of("field", "degree", "location"))
            if (profile.path(key).asText().length() > 1) filled++;
          for (String key :
              List.of("cgpa", "attendance", "familyIncome", "sopQuality", "recommendationStrength"))
            if (profile.path(key).asDouble() > 0) filled++;
          if (profile.path("achievements").asDouble() > 0
              || profile.path("hackathons").asDouble() > 0) filled++;
          if (profile.path("researchPapers").asDouble() > 0
              || profile.path("projects").asDouble() > 0) filled++;
          if (profile.path("leadershipRoles").asDouble() > 0
              || profile.path("volunteerHours").asDouble() > 0) filled++;
          if (input.path("skills").asText().trim().length() > 1) filled++;
          profile.put("profileCompletion", Math.round(filled / 12.0 * 100));
          ((ObjectNode) state.get("students")).set(user.id(), profile);
          audit(state, user, "profile.updated", "student", user.id());
          return null;
        });
  }
}
