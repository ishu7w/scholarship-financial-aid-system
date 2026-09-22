package com.scholarai.chat;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.Principal;
import com.scholarai.scholarship.*;
import com.scholarai.scoring.*;
import com.scholarai.storage.*;
import com.scholarai.student.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public final class ChatService {
  private final PlatformRepository repository;
  private final ObjectMapper json;
  private final ScoreService scores = new ScoreService();
  private static final String NO_PROFILE =
      "I don't have an academic profile on your account yet, so I can't quote a score or matches."
          + " Complete your student profile and I'll answer from your real numbers.";

  public ChatService(PlatformRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  private List<JsonNode> rows(JsonNode values) {
    return StreamSupport.stream(values.spliterator(), false).toList();
  }

  private String join(JsonNode values, String separator) {
    return String.join(separator, rows(values).stream().map(JsonNode::asText).toList());
  }

  private String value(JsonNode row, String key) {
    return row.path(key).asText();
  }

  private boolean matches(String text, String pattern) {
    return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
  }

  public ObjectNode context(Principal user) {
    var state = repository.read();
    ObjectNode context = json.createObjectNode().put("mode", user.demo() ? "demo" : "live");
    context
        .putObject("viewer")
        .put("id", user.id())
        .put("name", user.name())
        .put("role", user.role());
    context.putNull("student");
    context.putNull("score");
    context.putArray("matches");
    context.putArray("roadmap");
    List<Scholarship> scholarships = new ArrayList<>();
    for (JsonNode row : state.path("scholarships"))
      if (row.path("status").asText().equals("active")) {
        ObjectNode data = ((ObjectNode) row).deepCopy();
        data.remove(List.of("status", "institutionId"));
        scholarships.add(json.convertValue(data, Scholarship.class));
      }
    context
        .putObject("eligibility")
        .put("eligibleCount", 0)
        .put("trackedCount", scholarships.size());
    Set<String> applied = new HashSet<>();
    ArrayNode applications = context.putArray("applications");
    for (JsonNode row : state.path("applications"))
      if (row.path("studentId").asText().equals(user.id())) {
        ObjectNode application = ((ObjectNode) row).deepCopy();
        String id = value(row, "scholarshipId");
        applied.add(id);
        var sch = Records.find(state.path("scholarships"), "id", id);
        application.put("scholarshipName", sch == null ? id : value(sch, "name"));
        application.remove(List.of("studentId", "documentsVerified", "documentsTotal", "id"));
        applications.add(application);
      }
    ArrayNode deadlines = context.putArray("deadlines");
    scholarships.stream()
        .sorted(Comparator.comparing(Scholarship::deadline))
        .limit(6)
        .forEach(
            s -> {
              long remaining =
                  Math.max(
                      0,
                      (long)
                          Math.ceil(
                              (LocalDate.parse(s.deadline().substring(0, 10))
                                          .atStartOfDay(ZoneOffset.UTC)
                                          .toInstant()
                                          .toEpochMilli()
                                      - System.currentTimeMillis())
                                  / 86400000.0));
              deadlines
                  .addObject()
                  .put("scholarshipId", s.id())
                  .put("name", s.name())
                  .put("deadline", s.deadline())
                  .put("daysRemaining", remaining)
                  .put("applied", applied.contains(s.id()));
            });
    JsonNode stored = state.path("students").path(user.id());
    if (stored.isMissingNode()) return context;
    StudentProfile student = json.convertValue(stored, StudentProfile.class);
    ObjectNode publicProfile = ((ObjectNode) stored).deepCopy();
    publicProfile.retain(
        List.of(
            "name",
            "field",
            "degree",
            "year",
            "location",
            "cgpa",
            "attendance",
            "sopQuality",
            "recommendationStrength",
            "profileCompletion",
            "researchPapers",
            "leadershipRoles",
            "certifications",
            "volunteerHours",
            "skills"));
    context.set("student", publicProfile);
    var score = scores.calculate(student);
    ObjectNode scoreData = json.valueToTree(score);
    for (JsonNode component : scoreData.path("components")) {
      ((ObjectNode) component)
          .put("weightPercent", Math.round(component.path("weight").asDouble() * 100));
      ((ObjectNode) component).remove(List.of("key", "weight"));
    }
    context.set("score", scoreData);
    var ranked = new MatchingService(scores).rank(student, scholarships);
    ((ObjectNode) context.get("eligibility"))
        .put("eligibleCount", ranked.stream().filter(MatchingService.Match::eligible).count());
    List<MatchingService.Match> selected = new ArrayList<>(ranked.stream().limit(8).toList());
    ranked.stream()
        .filter(m -> !m.eligible())
        .findFirst()
        .ifPresent(
            m -> {
              if (!selected.contains(m)) selected.add(m);
            });
    ArrayNode result = context.putArray("matches");
    for (var match : selected) {
      ObjectNode row = json.valueToTree(match);
      row.remove("scholarship");
      row.remove("fairnessNote");
      var s = match.scholarship();
      row.put("scholarshipId", s.id())
          .put("name", s.name())
          .put("provider", s.provider())
          .put("category", s.category())
          .put("amount", s.amount())
          .put("currency", s.currency())
          .put("deadline", s.deadline());
      result.add(row);
    }
    context.set("roadmap", json.valueToTree(new RoadmapService(scores).create(student)));
    return context;
  }

  public String answer(Principal user, String question) {
    JsonNode ctx = context(user), score = ctx.path("score"), student = ctx.path("student");
    var matches = rows(ctx.path("matches"));
    var applications = rows(ctx.path("applications"));
    var roadmap = rows(ctx.path("roadmap"));
    var deadlines = rows(ctx.path("deadlines"));
    if (matches(question, "score|profile")) {
      if (score.isNull()) return NO_PROFILE;
      String weights =
          String.join(
              ", ",
              rows(score.path("components")).stream()
                  .limit(4)
                  .map(c -> value(c, "label") + " " + value(c, "weightPercent") + "%")
                  .toList());
      String gaps = join(score.path("gaps"), ", ");
      return "Your AI profile score is "
          + value(score, "total")
          + "/100 with "
          + value(score, "confidence")
          + "% confidence. Top strengths: "
          + join(score.path("strengths"), ", ")
          + ". Biggest gaps: "
          + (gaps.isEmpty() ? "none — well balanced!" : gaps)
          + ". The score is a weighted sum — "
          + weights
          + ", and so on. Open AI Insights for the full breakdown.";
    }
    if (matches(question, "recommend|match|which scholarship|best scholarship")) {
      if (score.isNull()) return NO_PROFILE;
      var top = matches.stream().filter(m -> m.path("eligible").asBoolean()).limit(3).toList();
      if (top.isEmpty())
        return "You aren't currently eligible for any of the scholarships I track, so I have no"
            + " matches to rank. Ask me why you're blocked on a specific one and I'll quote"
            + " the exact criteria.";
      List<String> lines = new ArrayList<>();
      int i = 1;
      for (JsonNode m : top) {
        var currency = java.text.NumberFormat.getCurrencyInstance(Locale.US);
        currency.setCurrency(Currency.getInstance(value(m, "currency")));
        currency.setMaximumFractionDigits(0);
        currency.setMinimumFractionDigits(0);
        lines.add(
            i++
                + ". "
                + value(m, "name")
                + " — "
                + value(m, "matchScore")
                + "% match, ~"
                + value(m, "winProbability")
                + "% win probability ("
                + currency.format(m.path("amount").asDouble())
                + ")");
      }
      return "Based on your profile, your top matches are:\n"
          + String.join("\n", lines)
          + "\nEach match is explainable — tap a card to see exactly why it was selected.";
    }
    if (matches(question, "eligib|qualify")) {
      if (score.isNull()) return NO_PROFILE;
      var blockers =
          matches.stream()
              .filter(m -> !m.path("eligible").asBoolean())
              .flatMap(m -> rows(m.path("missingCriteria")).stream())
              .map(JsonNode::asText)
              .distinct()
              .limit(2)
              .toList();
      return "You're currently eligible for "
          + value(ctx.path("eligibility"), "eligibleCount")
          + " of "
          + value(ctx.path("eligibility"), "trackedCount")
          + " tracked scholarships."
          + (blockers.isEmpty()
              ? ""
              : " The most common blockers on the rest: " + String.join("; ", blockers) + ".")
          + (student.isNull() ? "" : " Your CGPA of " + value(student, "cgpa") + " is on file.");
    }
    if (matches(question, "improve|better|roadmap|suggest")) {
      if (score.isNull()) return NO_PROFILE;
      if (roadmap.isEmpty())
        return "Your profile has no outstanding gaps in the roadmap right now — the engine has"
            + " nothing to recommend.";
      return "Here's your highest-impact plan:\n"
          + String.join(
              "\n",
              roadmap.stream()
                  .limit(3)
                  .map(
                      r ->
                          "• "
                              + value(r, "quarter")
                              + ": "
                              + value(r, "title")
                              + " (+"
                              + value(r, "impact")
                              + " pts projected)")
                  .toList())
          + "\nFull personalized roadmap is on your dashboard.";
    }
    if (matches(question, "sop|statement")) {
      if (student.isNull()) return NO_PROFILE;
      return "Your SOP currently scores "
          + value(student, "sopQuality")
          + "/100. To improve: 1) open with a specific moment, not a generic ambition; 2) quantify"
          + " outcomes (\"built X used by Y people\"); 3) name the exact program and why it fits;"
          + " 4) close with a 5-year goal. Want a paragraph-by-paragraph template? Check Help"
          + " Center → SOP Guide.";
    }
    if (matches(question, "reject|why not|denied")) {
      var rejected =
          applications.stream()
              .filter(a -> value(a, "status").equals("rejected"))
              .findFirst()
              .orElse(null);
      if (rejected != null) {
        String reasons = join(rejected.path("rejectionReasons"), "; ");
        JsonNode snapshot = rejected.path("aiSnapshot");
        return "Your application to "
            + value(rejected, "scholarshipName")
            + " was rejected. The recorded reasons: "
            + (reasons.isEmpty() ? "no reasons were recorded on the decision" : reasons)
            + "."
            + (snapshot.isNull()
                ? ""
                : " Your frozen score at submit time was "
                    + value(snapshot, "total")
                    + "/100 with a "
                    + value(snapshot, "matchScore")
                    + "% match.")
            + " That's the full decision record — nothing hidden.";
      }
      var blocked =
          matches.stream().filter(m -> !m.path("eligible").asBoolean()).findFirst().orElse(null);
      if (blocked != null)
        return "You have no rejected applications right now. But take "
            + value(blocked, "name")
            + ": you're not eligible because — "
            + join(blocked.path("missingCriteria"), "; ")
            + ". That's the full reason; nothing hidden. Fix path: "
            + blocked.path("improvements").path(0).asText("review criteria on the scholarship page")
            + ".";
      return "You have no rejected applications right now.";
    }
    if (matches(question, "status|applied|application")) {
      if (applications.isEmpty())
        return "You haven't submitted any applications yet. Once you apply, I can quote each one's"
            + " status and its frozen score.";
      return "Your applications:\n"
          + String.join(
              "\n",
              applications.stream()
                  .map(
                      a ->
                          "• "
                              + value(a, "scholarshipName")
                              + " — "
                              + value(a, "status").replace('_', ' ')
                              + (a.path("aiSnapshot").isNull()
                                  ? ""
                                  : " (frozen score "
                                      + value(a.path("aiSnapshot"), "total")
                                      + "/100)"))
                  .toList());
    }
    if (matches(question, "deadline|when|date")) {
      if (deadlines.isEmpty()) return "I don't have any scholarship deadlines on file right now.";
      JsonNode soon = deadlines.get(0);
      String date =
          LocalDate.parse(value(soon, "deadline").substring(0, 10))
              .format(DateTimeFormatter.ofPattern("MMMM d", Locale.US));
      return "Closest deadline: "
          + value(soon, "name")
          + " on "
          + date
          + " — "
          + value(soon, "daysRemaining")
          + " days away. Your dashboard calendar tracks all "
          + value(ctx.path("eligibility"), "trackedCount")
          + " deadlines with reminders.";
    }
    if (matches(question, "fraud|verify|document"))
      return "Documents are verified through the OCR pipeline: text extraction → field validation →"
          + " cross-document consistency checks → anomaly flags. Verification status appears"
          + " on each document card. Institutions only see the verification result, never"
          + " your raw documents, unless you apply.";
    if (matches(question, "hello|hi|hey"))
      return "Hi "
          + (student.isNull() ? user.name() : value(student, "name")).split(" ")[0]
          + "! I'm your scholarship copilot. Ask me about your score, recommendations, eligibility,"
          + " deadlines, or how to improve your profile.";
    return "I can help with: \"What's my profile score?\", \"Which scholarships match me?\", \"Why"
        + " was I rejected?\", \"How do I improve?\", \"What deadlines are coming?\", or"
        + " \"How does document verification work?\"";
  }
}
