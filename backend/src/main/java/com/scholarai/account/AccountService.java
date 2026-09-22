package com.scholarai.account;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.*;
import com.scholarai.storage.*;
import java.time.Instant;
import java.util.*;

/** Supabase verifies passwords; this module owns roles and profile records. */
public final class AccountService {
  private final PlatformRepository repository;
  private final ObjectMapper json;

  public AccountService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public JsonNode get(Principal user) {
    return find(repository.read().path("users"), "id", user.id());
  }

  public void register(Principal user, JsonNode input) {
    String role = text(input, "role", 1, 20);
    if (!Set.of("student", "institution").contains(role))
      throw new AidException(400, "Invalid account role");
    String name = text(input, "name", 2, 200), email = text(input, "email", 3, 254);
    repository.update(
        state -> {
          if (find(state.path("users"), "id", user.id()) != null) return null;
          ((ArrayNode) state.get("users"))
              .addObject()
              .put("id", user.id())
              .put("name", name)
              .put("email", email)
              .put("role", role)
              .put("disabled", false)
              .put("avatarHue", Math.abs(email.hashCode() % 360))
              .put("createdAt", Instant.now().toString());
          if (role.equals("student")) {
            ObjectNode profile =
                json.createObjectNode()
                    .put("id", user.id())
                    .put("name", name)
                    .put("email", email)
                    .put("avatarHue", Math.abs(email.hashCode() % 360));
            profile
                .put("field", input.path("field").asText(""))
                .put("degree", "")
                .put("location", "")
                .put("gender", "other")
                .put("year", 1);
            for (String key : List.of("minority", "disability", "firstGeneration"))
              profile.put(key, false);
            for (String key :
                List.of(
                    "attendance",
                    "familyIncome",
                    "achievements",
                    "researchPapers",
                    "hackathons",
                    "sportsLevel",
                    "certifications",
                    "leadershipRoles",
                    "volunteerHours",
                    "projects",
                    "previousScholarships",
                    "sopQuality",
                    "recommendationStrength")) profile.put(key, 0);
            profile
                .put("cgpa", input.hasNonNull("cgpa") ? number(input, "cgpa", 0, 10, false) : 0)
                .put("behaviourScore", 100);
            profile.putArray("skills");
            int filled = 2;
            if (input.path("cgpa").asDouble() > 0) filled++;
            if (input.path("year").asInt() > 0) filled++;
            if (!input.path("field").asText().isBlank()) filled++;
            if (!input.path("achievementsText").asText().isBlank()) filled++;
            profile.put("profileCompletion", filled * 10);
            ((ObjectNode) state.get("students")).set(user.id(), profile);
          }
          audit(state, user, "account.registered", "user", user.id());
          return null;
        });
  }
}
