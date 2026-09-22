package com.scholarai.http;

import static com.scholarai.storage.Records.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.admin.AdminService;
import com.scholarai.aid.domain.*;
import com.scholarai.application.ApplicationService;
import com.scholarai.institution.InstitutionService;
import com.scholarai.notification.NotificationService;
import com.scholarai.resume.ResumeService;
import com.scholarai.scholarship.*;
import com.scholarai.scoring.*;
import com.scholarai.storage.*;
import com.scholarai.student.*;
import java.util.*;

/** HTTP routing only. Each module owns its own rules. */
public final class PlatformApi {
  private final ObjectMapper json;
  private final PlatformRepository repository;
  private final StudentService students;
  private final ApplicationService applications;
  private final InstitutionService institutions;
  private final AdminService admin;
  private final NotificationService notifications;
  private final ScoreService scores = new ScoreService();
  private final MatchingService matching = new MatchingService(scores);

  public PlatformApi(ObjectMapper json, PlatformRepository repository) {
    this.json = json;
    this.repository = repository;
    students = new StudentService(repository, json);
    applications = new ApplicationService(repository, json, scores, matching);
    institutions = new InstitutionService(repository, json);
    admin = new AdminService(repository, json);
    notifications = new NotificationService(repository, json);
  }

  public Object handle(Principal user, String operation, JsonNode input) {
    if (input == null || !input.isObject()) throw new AidException(400, "Expected a JSON object");
    String id = input.path("id").asText();
    switch (operation) {
      case "chat-context":
        return new com.scholarai.chat.ChatService(repository, json).context(user);
      case "chat":
        return new com.scholarai.chat.ChatService(repository, json)
            .answer(user, text(input, "question", 1, 10000));
      case "account":
        return new com.scholarai.account.AccountService(repository, json).get(user);
      case "register":
        new com.scholarai.account.AccountService(repository, json).register(user, input);
        return Map.of("ok", true);
      case "scholarships":
        return catalogue();
      case "scholarship":
        return find(catalogue(), "id", id);
      case "student":
        return students.get(user, id);
      case "save-profile":
        students.save(user, input);
        return Map.of("ok", true);
      case "applications":
        return applications.list(user, id);
      case "apply":
        applications.submit(user, id);
        return Map.of("ok", true);
      case "withdraw":
        applications.withdraw(user, id);
        return Map.of("ok", true);
      case "saved":
        return applications.saved(user, id);
      case "toggle-save":
        return Map.of("ok", true, "saved", applications.toggleSave(user, id));
      case "institution":
        return institutions.institution(user, id);
      case "institution-scholarships":
        return institutions.scholarships(user, id);
      case "institution-ranked":
        return institutions.rankedApplicants(user, id);
      case "institution-applicants":
        return institutions.applicants(user, id);
      case "institution-aggregates":
        return institutions.aggregates(user, id);
      case "create-scholarship":
        return Map.of("ok", true, "id", institutions.save(user, null, input));
      case "update-scholarship":
        institutions.save(user, id, input.path("data"));
        return Map.of("ok", true);
      case "decide":
        institutions.decide(user, input);
        return Map.of("ok", true);
      case "score":
        return scores.calculate(student(input));
      case "fraud":
        return new FraudService().assess(student(input));
      case "roadmap":
        return new RoadmapService(scores).create(student(input));
      case "match":
        return matching.match(
            student(input), json.convertValue(input.path("scholarship"), Scholarship.class));
      case "rank":
        return matching.rank(
            student(input),
            Arrays.asList(json.convertValue(input.path("scholarships"), Scholarship[].class)));
      case "resume":
        return new ResumeService().analyze(text(input, "text", 0, 200000));
      case "admin-stats":
        return admin.stats(user);
      case "admin-users":
        return admin.users(user);
      case "admin-audit":
        return admin.auditPage(user, input);
      case "admin-role":
        admin.updateUser(user, input, true);
        return Map.of("ok", true);
      case "admin-disabled":
        admin.updateUser(user, input, false);
        return Map.of("ok", true);
      case "notifications":
        return notifications.feed(user);
      case "notifications-read":
        return notifications.markRead(user, input.hasNonNull("id") ? id : null);
      case "documents":
        return new com.scholarai.document.DocumentService(repository, json).list(user, id);
      case "document-save":
        return new com.scholarai.document.DocumentService(repository, json).save(user, input);
      case "document-delete":
        new com.scholarai.document.DocumentService(repository, json).delete(user, id);
        return Map.of("ok", true);
      default:
        throw new AidException(404, "Endpoint not found.");
    }
  }

  private StudentProfile student(JsonNode input) {
    return json.convertValue(input.path("student"), StudentProfile.class);
  }

  private ArrayNode catalogue() {
    ArrayNode result = json.createArrayNode();
    for (JsonNode sch : repository.read().path("scholarships"))
      if (sch.path("status").asText().equals("active")) {
        ObjectNode item = ((ObjectNode) sch).deepCopy();
        item.remove(List.of("status", "institutionId"));
        result.add(item);
      }
    return result;
  }
}
