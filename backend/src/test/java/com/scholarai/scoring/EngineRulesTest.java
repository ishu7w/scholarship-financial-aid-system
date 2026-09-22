package com.scholarai.scoring;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scholarai.Main;
import com.scholarai.resume.ResumeService;
import com.scholarai.scholarship.*;
import com.scholarai.student.*;
import java.util.*;
import org.junit.jupiter.api.*;

class EngineRulesTest {
  private final com.fasterxml.jackson.databind.ObjectMapper json = Main.json();
  private ObjectNode student;
  private List<Scholarship> scholarships;
  private final ScoreService scores = new ScoreService();

  @BeforeEach
  void setup() throws Exception {
    var seed = json.readTree(getClass().getResourceAsStream("/demo-data.json"));
    student = (ObjectNode) seed.get("student");
    scholarships = Arrays.asList(json.treeToValue(seed.get("scholarships"), Scholarship[].class));
  }

  private StudentProfile profile(ObjectNode data) {
    return json.convertValue(data, StudentProfile.class);
  }

  @Test
  void increasingCgpaNeverDecreasesTotal() {
    for (int attendance : new int[] {0, 45, 88, 100}) {
      long previous = -1;
      for (int step = 0; step <= 200; step++) {
        student.put("cgpa", step / 20.0).put("attendance", attendance);
        long total = scores.calculate(profile(student)).total();
        assertTrue(total >= previous);
        previous = total;
      }
    }
  }

  @Test
  void increasingIncomeNeverIncreasesNeed() {
    long previous = 101;
    for (int income = 0; income <= 2000000; income += 977) {
      student.put("familyIncome", income);
      long need = scores.calculate(profile(student)).components().get(1).raw();
      assertTrue(need <= previous);
      previous = need;
    }
  }

  @Test
  void scoresAndMatchesStayBoundedAcrossRandomProfiles() {
    Random random = new Random(7);
    var matching = new MatchingService(scores);
    for (int i = 0; i < 2000; i++) {
      student
          .put("cgpa", random.nextDouble() * 10)
          .put("attendance", random.nextDouble() * 100)
          .put("familyIncome", random.nextInt(3000000));
      for (String key :
          List.of(
              "achievements",
              "researchPapers",
              "hackathons",
              "projects",
              "certifications",
              "leadershipRoles")) student.put(key, random.nextInt(20));
      for (String key :
          List.of("sopQuality", "recommendationStrength", "behaviourScore", "profileCompletion"))
        student.put(key, random.nextInt(101));
      var p = profile(student);
      var score = scores.calculate(p);
      assertTrue(score.total() >= 0 && score.total() <= 100);
      assertTrue(score.confidence() >= 0 && score.confidence() <= 100);
      for (var part : score.components()) assertTrue(part.raw() >= 0 && part.raw() <= 100);
      if (i < 120)
        for (var match : matching.rank(p, scholarships)) {
          assertTrue(match.matchScore() >= 0 && match.matchScore() <= 100);
          assertTrue(match.winProbability() >= 0 && match.winProbability() <= 100);
        }
    }
  }

  @Test
  void protectedAttributesNeverAlterBaseScoreOrFraudReport() {
    var first =
        profile(
            student
                .deepCopy()
                .put("gender", "female")
                .put("minority", true)
                .put("disability", true));
    var second =
        profile(
            student
                .deepCopy()
                .put("gender", "male")
                .put("minority", false)
                .put("disability", false));
    assertEquals(scores.calculate(first), scores.calculate(second));
    assertEquals(new FraudService().assess(first), new FraudService().assess(second));
    var neutral =
        scholarships.stream()
            .filter(s -> s.id().equals("sch-merit-excellence"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        new MatchingService(scores).match(first, neutral),
        new MatchingService(scores).match(second, neutral));
  }

  @Test
  void eligibilityExplainsIndependentFailures() {
    var research =
        scholarships.stream()
            .filter(s -> s.criteria().requiresResearch())
            .findFirst()
            .orElseThrow();
    var match =
        new MatchingService(scores)
            .match(
                profile(student.put("cgpa", 0).put("attendance", 0).put("researchPapers", 0)),
                research);
    assertFalse(match.eligible());
    assertTrue(match.missingCriteria().stream().anyMatch(s -> s.contains("CGPA")));
    assertTrue(match.missingCriteria().stream().anyMatch(s -> s.contains("Attendance")));
    assertTrue(match.missingCriteria().stream().anyMatch(s -> s.contains("publication")));
  }

  @Test
  void fraudCombinesSignalsAndCapsRisk() {
    var report =
        new FraudService()
            .assess(
                profile(
                    student
                        .put("cgpa", 9.9)
                        .put("attendance", 20)
                        .put("familyIncome", 1000)
                        .put("certifications", 10)
                        .put("researchPapers", 8)
                        .put("year", 1)
                        .put("previousScholarships", 5)
                        .put("behaviourScore", 10)));
    assertEquals(100, report.riskScore());
    assertEquals("flagged", report.level());
    assertEquals(5, report.signals().size());
  }

  @Test
  void roadmapRemainsActionableForStrongAndWeakProfiles() {
    var roadmap = new RoadmapService(scores);
    var weak =
        roadmap.create(
            profile(
                student
                    .put("sopQuality", 0)
                    .put("researchPapers", 0)
                    .put("leadershipRoles", 0)
                    .put("volunteerHours", 0)));
    assertTrue(weak.size() > 3);
    assertTrue(weak.stream().allMatch(i -> i.impact() >= 0));
    assertTrue(weak.stream().anyMatch(i -> i.title().contains("Statement of Purpose")));
    assertTrue(
        roadmap
            .create(
                profile(
                    student
                        .put("sopQuality", 100)
                        .put("researchPapers", 5)
                        .put("projects", 20)
                        .put("certifications", 10)
                        .put("leadershipRoles", 5)
                        .put("volunteerHours", 500)))
            .stream()
            .anyMatch(i -> i.quarter().equals("Q4")));
  }

  @Test
  void resumeExtractsSkillsAndRewardsMeasurableWork() {
    var service = new ResumeService();
    String text =
        "Bachelor at State University\n"
            + "Built a Java SQL React project for 500 students\n"
            + "Software engineer internship improved performance by 30%\n"
            + "Winner of a hackathon scholarship\n"
            + "AWS certified course";
    var result = service.analyze(text);
    assertTrue(result.extracted().get("skills").contains("Java"));
    assertFalse(result.extracted().get("education").isEmpty());
    assertFalse(result.suggestions().stream().anyMatch(s -> s.startsWith("Quantify")));
    assertTrue(result.atsScore() > service.analyze("A brief resume").atsScore());
  }
}
