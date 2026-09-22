package com.scholarai.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.Main;
import com.scholarai.aid.domain.*;
import com.scholarai.http.PlatformApi;
import com.scholarai.storage.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class PlatformWorkflowTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
  private final ObjectMapper json = Main.json();
  private final Principal student = new Principal("stu-aarya", "Aarya Sharma", "student", true);
  private final Principal institution =
      new Principal("ins-statuni", "State University", "institution", true);
  private PlatformRepository repository;
  private PlatformApi api;
  private String url;

  @BeforeEach
  void setup() throws Exception {
    url = "jdbc:h2:file:" + directory.resolve("platform");
    repository = new JdbcPlatformRepository(url, json, DemoData.load(json));
    api = new PlatformApi(json, repository);
    repository.update(
        state -> {
          for (JsonNode sch : state.path("scholarships"))
            ((ObjectNode) sch).put("deadline", "2099-12-31");
          return null;
        });
  }

  private JsonNode call(Principal user, String op, JsonNode input) {
    return json.valueToTree(api.handle(user, op, input));
  }

  private ObjectNode id(String value) {
    return json.createObjectNode().put("id", value);
  }

  private String scholarship() {
    return call(student, "scholarships", json.createObjectNode()).get(0).path("id").asText();
  }

  @Test
  void catalogueAndInstitutionQueuePreserveSeed() {
    assertEquals(12, call(student, "scholarships", json.createObjectNode()).size());
    assertEquals(60, call(institution, "institution-applicants", id("demo-institution")).size());
    assertTrue(call(student, "scholarship", id("missing")).isNull());
    int total = 0;
    for (JsonNode row :
        call(institution, "institution-aggregates", id("demo-institution")).path("categories"))
      total += row.path("value").asInt();
    assertEquals(60, total);
  }

  @Test
  void profileEditsPersistAndCannotChangeProtectedFields() throws Exception {
    ObjectNode input = (ObjectNode) call(student, "student", id(student.id()));
    input
        .put("skills", "Java, SQL")
        .put("name", "Ishu Patel")
        .put("cgpa", 9.7)
        .put("behaviourScore", 0)
        .put("id", "someone-else");
    call(student, "save-profile", input);
    PlatformApi restarted =
        new PlatformApi(json, new JdbcPlatformRepository(url, json, DemoData.load(json)));
    JsonNode saved = json.valueToTree(restarted.handle(student, "student", id(student.id())));
    assertEquals("Ishu Patel", saved.path("name").asText());
    assertEquals(9.7, saved.path("cgpa").asDouble());
    assertEquals(student.id(), saved.path("id").asText());
    assertNotEquals(0, saved.path("behaviourScore").asInt());
    assertEquals(2, saved.path("skills").size());
  }

  @Test
  void profileValidationAndOwnershipAreEnforced() {
    ObjectNode input = (ObjectNode) call(student, "student", id(student.id()));
    input.put("skills", "Java").put("cgpa", 11);
    assertEquals(
        400, assertThrows(AidException.class, () -> call(student, "save-profile", input)).status());
    assertEquals(
        403,
        assertThrows(AidException.class, () -> call(student, "student", id("stu-100"))).status());
    assertEquals(
        403,
        assertThrows(AidException.class, () -> call(institution, "save-profile", input)).status());
  }

  @Test
  void submitFreezeDecideAndNotify() {
    String sch = scholarship();
    call(student, "apply", id(sch));
    ObjectNode app = (ObjectNode) call(student, "applications", id(student.id())).get(0);
    JsonNode snapshot = app.path("aiSnapshot").deepCopy();
    ObjectNode profile = (ObjectNode) call(student, "student", id(student.id()));
    profile.put("skills", "Java").put("cgpa", 1);
    call(student, "save-profile", profile);
    assertEquals(
        snapshot, call(student, "applications", id(student.id())).get(0).path("aiSnapshot"));
    call(
        institution,
        "decide",
        json.createObjectNode()
            .put("applicationId", app.path("id").asText())
            .put("decision", "approve"));
    assertEquals(
        "approved", call(student, "applications", id(student.id())).get(0).path("status").asText());
    assertEquals(1, call(student, "notifications", json.createObjectNode()).path("unread").asInt());
    assertThrows(AidException.class, () -> call(student, "withdraw", id(sch)));
    assertThrows(
        AidException.class,
        () ->
            call(
                institution,
                "decide",
                json.createObjectNode()
                    .put("applicationId", app.path("id").asText())
                    .put("decision", "reject")));
    call(student, "notifications-read", json.createObjectNode());
    assertEquals(0, call(student, "notifications", json.createObjectNode()).path("unread").asInt());
  }

  @Test
  void withdrawalAndSavedStateSurviveRepositoryReopen() throws Exception {
    String sch = scholarship();
    call(student, "apply", id(sch));
    call(student, "withdraw", id(sch));
    assertEquals(0, call(student, "applications", id(student.id())).size());
    assertTrue(call(student, "toggle-save", id(sch)).path("saved").asBoolean());
    var reopened =
        new PlatformApi(json, new JdbcPlatformRepository(url, json, DemoData.load(json)));
    assertEquals(1, json.valueToTree(reopened.handle(student, "saved", id(student.id()))).size());
    assertFalse(call(student, "toggle-save", id(sch)).path("saved").asBoolean());
  }

  @Test
  void concurrentSubmissionCreatesExactlyOneApplication() throws Exception {
    String sch = scholarship();
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Boolean>> jobs =
          List.of(
              () -> {
                try {
                  call(student, "apply", id(sch));
                  return true;
                } catch (AidException e) {
                  return false;
                }
              },
              () -> {
                try {
                  call(student, "apply", id(sch));
                  return true;
                } catch (AidException e) {
                  return false;
                }
              });
      int success = 0;
      for (Future<Boolean> result : threads.invokeAll(jobs)) if (result.get()) success++;
      assertEquals(1, success);
      assertEquals(1, call(student, "applications", id(student.id())).size());
    } finally {
      threads.shutdownNow();
    }
  }

  @Test
  void deadlinesAndRolesAreCheckedInsideJava() {
    String sch = scholarship();
    repository.update(
        state -> {
          Records.find(state.path("scholarships"), "id", sch).put("deadline", "2000-01-01");
          return null;
        });
    assertEquals(
        409, assertThrows(AidException.class, () -> call(student, "apply", id(sch))).status());
    assertEquals(
        403, assertThrows(AidException.class, () -> call(institution, "apply", id(sch))).status());
    assertEquals(
        403,
        assertThrows(
                AidException.class,
                () ->
                    call(
                        student,
                        "admin-disabled",
                        json.createObjectNode().put("userId", "adm-root").put("disabled", true)))
            .status());
  }

  @Test
  void institutionCanPublishEditAndCannotAccessAnotherInstitutionsPrograms() {
    Principal owner = new Principal("owner", "University", "institution", false);
    Principal other = new Principal("other", "Other University", "institution", false);
    ObjectNode input =
        json.createObjectNode()
            .put("name", "Java Scholarship")
            .put("provider", "University")
            .put("category", "Merit")
            .put("amount", "5000")
            .put("currency", "USD")
            .put("deadline", "2099-12-31")
            .put("seats", "10")
            .put(
                "description",
                "A scholarship for students studying Java and object oriented programming.")
            .put("tags", "java, programming")
            .put("status", "active")
            .put("minCgpa", "7")
            .put("maxIncome", "")
            .put("minAttendance", "70")
            .put("locations", "")
            .put("fields", "");
    for (String key :
        List.of(
            "requiresResearch",
            "requiresLeadership",
            "womenOnly",
            "minorityOnly",
            "sportsRequired",
            "disabilityPreferred")) input.put(key, false);
    String id = call(owner, "create-scholarship", input).path("id").asText();
    assertEquals(13, call(student, "scholarships", json.createObjectNode()).size());
    input.put("name", "Updated Java Scholarship");
    ObjectNode update = json.createObjectNode().put("id", id);
    update.set("data", input);
    assertEquals(
        403,
        assertThrows(AidException.class, () -> call(other, "update-scholarship", update)).status());
    call(owner, "update-scholarship", update);
    assertEquals(
        "Updated Java Scholarship", call(student, "scholarship", id(id)).path("name").asText());
    assertEquals(
        403,
        assertThrows(
                AidException.class,
                () -> call(other, "institution-applicants", id("institution-owner")))
            .status());
    input.put("status", "closed");
    call(owner, "update-scholarship", update);
    assertEquals(12, call(student, "scholarships", json.createObjectNode()).size());
    assertEquals(
        409, assertThrows(AidException.class, () -> call(student, "apply", id(id))).status());
  }

  @Test
  void accountRegistrationCannotCreateOrEscalateAnAdmin() {
    Principal fresh = new Principal("new-student", "New Student", "student", false);
    ObjectNode input =
        json.createObjectNode()
            .put("name", "New Student")
            .put("email", "new@example.com")
            .put("role", "admin");
    assertEquals(
        400, assertThrows(AidException.class, () -> call(fresh, "register", input)).status());
    input.put("role", "student").put("cgpa", 8.5).put("field", "Computer Science");
    call(fresh, "register", input);
    assertEquals("student", call(fresh, "account", json.createObjectNode()).path("role").asText());
    assertEquals(8.5, call(fresh, "student", id(fresh.id())).path("cgpa").asDouble());
    input.put("role", "institution");
    call(fresh, "register", input);
    assertEquals("student", call(fresh, "account", json.createObjectNode()).path("role").asText());
    Principal admin = new Principal("adm-root", "Administrator", "admin", false);
    call(
        admin,
        "admin-disabled",
        json.createObjectNode().put("userId", fresh.id()).put("disabled", true));
    assertTrue(call(fresh, "account", json.createObjectNode()).path("disabled").asBoolean());
    assertEquals(
        400,
        assertThrows(
                AidException.class,
                () ->
                    call(
                        admin,
                        "admin-role",
                        json.createObjectNode().put("userId", admin.id()).put("role", "student")))
            .status());
  }

  @Test
  void documentRecordsUseStoredClaimsAndAreOwnerScoped() {
    ObjectNode input =
        json.createObjectNode()
            .put("kind", "id")
            .put("mimeType", "application/pdf")
            .put("byteSize", 100)
            .put("storagePath", student.id() + "/id.pdf")
            .put("text", "Issued to Aarya Sharma");
    JsonNode document = call(student, "document-save", input);
    assertEquals("verified", document.path("verificationStatus").asText());
    assertEquals(1, call(student, "documents", id(student.id())).size());
    Principal other = new Principal("other", "Other Student", "student", true);
    assertEquals(
        403,
        assertThrows(
                AidException.class,
                () -> call(other, "document-delete", id(document.path("id").asText())))
            .status());
    input.put("storagePath", "someone-else/id.pdf");
    assertEquals(
        403,
        assertThrows(AidException.class, () -> call(student, "document-save", input)).status());
    call(student, "document-delete", id(document.path("id").asText()));
    assertEquals(0, call(student, "documents", id(student.id())).size());
  }

  @Test
  void copilotUsesTheCallersStoredProfileAndApplications() {
    JsonNode profile = call(student, "student", id(student.id()));
    JsonNode expected = call(student, "score", json.createObjectNode().set("student", profile));
    JsonNode context = call(student, "chat-context", json.createObjectNode());
    assertEquals(expected.path("total"), context.path("score").path("total"));
    assertEquals(12, context.path("eligibility").path("trackedCount").asInt());
    String answer =
        call(student, "chat", json.createObjectNode().put("question", "What is my score?"))
            .asText();
    assertTrue(answer.contains(expected.path("total").asText() + "/100"));
    Principal unknown = new Principal("unknown", "New Student", "student", true);
    assertTrue(call(unknown, "chat-context", json.createObjectNode()).path("score").isNull());
    assertTrue(
        call(unknown, "chat", json.createObjectNode().put("question", "What is my score?"))
            .asText()
            .contains("don't have an academic profile"));
  }
}
