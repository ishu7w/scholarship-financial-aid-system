package com.scholarai.student;

import com.scholarai.scoring.ScoreService;
import java.util.*;

public final class RoadmapService {
  private final ScoreService scores;

  public RoadmapService(ScoreService scores) {
    this.scores = scores;
  }

  public record Item(String quarter, String title, String detail, long impact) {}

  public List<Item> create(StudentProfile s) {
    Map<String, Long> raw = new HashMap<>();
    scores.calculate(s).components().forEach(c -> raw.put(c.key(), c.raw()));
    List<Item> items = new ArrayList<>();
    if (raw.get("sop") < 80)
      items.add(
          new Item(
              "Q1",
              "Rewrite your Statement of Purpose",
              "Add measurable outcomes and a specific 5-year goal. SOP drives 8% of your total"
                  + " score.",
              Math.round((80 - raw.get("sop")) * .08)));
    if (raw.get("projects") < 70)
      items.add(
          new Item(
              "Q1",
              "Ship 2 portfolio projects in " + s.field(),
              "Public, documented projects raise both Projects and Skills sub-scores.",
              4));
    if (s.certifications() < 3)
      items.add(
          new Item(
              "Q2",
              "Earn an industry certification",
              "Recognized "
                  + s.field()
                  + " certifications strengthen merit and corporate scholarship matches.",
              3));
    if (raw.get("research") < 50)
      items.add(
          new Item(
              "Q2",
              "Join a research group",
              "Even one workshop paper unlocks research-gated scholarships worth 10% of scoring"
                  + " weight.",
              5));
    if (raw.get("leadership") < 60)
      items.add(
          new Item(
              "Q3",
              "Take a leadership role",
              "Lead a student club, hackathon team, or volunteer drive — verified roles compound"
                  + " quickly.",
              3));
    if (s.volunteerHours() < 150)
      items.add(
          new Item(
              "Q3",
              "Log " + ScoreService.number(150 - s.volunteerHours()) + " more volunteer hours",
              "Community service is weighted at 7% and heavily reviewed by need-based committees.",
              2));
    items.add(
        new Item(
            "Q4",
            "Apply early to top-5 matches",
            "Applications submitted in the first 20% of the window have historically higher"
                + " approval odds.",
            4));
    return items;
  }
}
