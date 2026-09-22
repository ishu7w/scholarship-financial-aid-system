package com.scholarai.scoring;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.scholarai.Main;
import com.scholarai.scholarship.*;
import com.scholarai.student.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EngineParityTest {
  @Test
  void preservesOriginalFrontendResultsForEverySeededStudent() throws Exception {
    var json = Main.json();
    var fixtures = json.readTree(getClass().getResourceAsStream("/engine-parity.json"));
    var seed = json.readTree(getClass().getResourceAsStream("/demo-data.json"));
    var scholarships =
        Arrays.asList(json.treeToValue(seed.get("scholarships"), Scholarship[].class));
    var scores = new ScoreService();
    for (JsonNode fixture : fixtures) {
      var student = json.treeToValue(fixture.get("student"), StudentProfile.class);
      assertJson(
          fixture.get("score"),
          json.valueToTree(scores.calculate(student)),
          student.id() + " score");
      assertJson(
          fixture.get("matches"),
          json.valueToTree(new MatchingService(scores).rank(student, scholarships)),
          student.id() + " matches");
      assertJson(
          fixture.get("fraud"),
          json.valueToTree(new FraudService().assess(student)),
          student.id() + " fraud");
      assertJson(
          fixture.get("roadmap"),
          json.valueToTree(new RoadmapService(scores).create(student)),
          student.id() + " roadmap");
    }
  }

  // JSON treats integer and floating-point representations as the same number.
  private void assertJson(JsonNode expected, JsonNode actual, String path) {
    if (expected.isNumber()) assertEquals(expected.asDouble(), actual.asDouble(), 0.000001, path);
    else if (expected.isContainerNode()) {
      assertEquals(expected.size(), actual.size(), path);
      if (expected.isArray())
        for (int i = 0; i < expected.size(); i++)
          assertJson(expected.get(i), actual.get(i), path + "/" + i);
      else
        expected
            .fields()
            .forEachRemaining(
                e -> assertJson(e.getValue(), actual.get(e.getKey()), path + "/" + e.getKey()));
    } else assertEquals(expected, actual, path);
  }
}
