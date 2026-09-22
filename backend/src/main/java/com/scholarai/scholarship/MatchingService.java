package com.scholarai.scholarship;

import static com.scholarai.scoring.ScoreService.*;

import com.scholarai.scoring.ScoreService;
import com.scholarai.student.StudentProfile;
import java.util.*;

public final class MatchingService {
  private final ScoreService scores;

  public MatchingService(ScoreService scores) {
    this.scores = scores;
  }

  public record Match(
      Scholarship scholarship,
      boolean eligible,
      long matchScore,
      long winProbability,
      List<String> missingCriteria,
      List<String> reasons,
      List<String> improvements,
      String fairnessNote) {}

  public Match match(StudentProfile s, Scholarship sch) {
    var c = sch.criteria();
    List<String> missing = new ArrayList<>(),
        reasons = new ArrayList<>(),
        improvements = new ArrayList<>();
    if (s.cgpa() < c.minCgpa()) {
      missing.add("CGPA " + fixed(s.cgpa(), 1) + " below required " + fixed(c.minCgpa(), 1));
      improvements.add(
          "Raise CGPA to "
              + fixed(c.minCgpa(), 1)
              + "+ — currently "
              + fixed(c.minCgpa() - s.cgpa(), 1)
              + " points short");
    } else
      reasons.add(
          "CGPA " + fixed(s.cgpa(), 1) + " clears the " + fixed(c.minCgpa(), 1) + " requirement");
    if (c.maxIncome() != null) {
      if (s.familyIncome() > c.maxIncome())
        missing.add("Family income exceeds cap of $" + number(c.maxIncome()));
      else reasons.add("Income within need-based cap ($" + number(c.maxIncome()) + ")");
    }
    if (s.attendance() < c.minAttendance()) {
      missing.add(
          "Attendance "
              + number(s.attendance())
              + "% below required "
              + number(c.minAttendance())
              + "%");
      improvements.add("Improve attendance to " + number(c.minAttendance()) + "%+");
    }
    if (c.requiresResearch() && s.researchPapers() == 0) {
      missing.add("Requires at least one research publication");
      improvements.add("Publish or co-author a research paper — even a workshop paper counts");
    } else if (c.requiresResearch())
      reasons.add(number(s.researchPapers()) + " publications satisfy the research requirement");
    if (c.requiresLeadership() && s.leadershipRoles() == 0) {
      missing.add("Requires demonstrated leadership");
      improvements.add("Take a club, team, or project leadership role this semester");
    } else if (c.requiresLeadership()) reasons.add("Leadership requirement met");
    if (c.womenOnly() && !s.gender().equals("female"))
      missing.add("Restricted to women applicants");
    if (c.minorityOnly() && !s.minority())
      missing.add("Restricted to minority-community applicants");
    if (c.sportsRequired() && s.sportsLevel() == 0)
      missing.add("Requires competitive sports participation");
    else if (c.sportsRequired())
      reasons.add("Sports level " + number(s.sportsLevel()) + "/3 qualifies");
    if (!c.locations().isEmpty() && !c.locations().contains(s.location()))
      missing.add("Limited to: " + String.join(", ", c.locations()));
    if (!c.fields().isEmpty() && !c.fields().contains(s.field()))
      missing.add("Limited to fields: " + String.join(", ", c.fields()));
    else if (!c.fields().isEmpty())
      reasons.add("Field of study (" + s.field() + ") is an exact match");
    boolean eligible = missing.isEmpty();
    var ai = scores.calculate(s);
    double affinity = ai.total() * .55;
    if (c.disabilityPreferred() && s.disability()) {
      affinity += 8;
      reasons.add("Disability-inclusive preference applies to your profile");
    }
    switch (sch.category()) {
      case "Research Grant" -> affinity += Math.min(s.researchPapers() * 6, 18);
      case "Sports" -> affinity += s.sportsLevel() * 8;
      case "Need-based" ->
          affinity +=
              ai.components().stream()
                      .filter(p -> p.key().equals("financialNeed"))
                      .findFirst()
                      .orElseThrow()
                      .raw()
                  * .25;
      case "Merit" -> affinity += s.cgpa() * 2.5;
    }
    if (s.skills().stream().anyMatch(sk -> sch.tags().contains(sk.toLowerCase(Locale.ROOT)))) {
      affinity += 6;
      reasons.add("Skill tags overlap with scholarship focus areas");
    }
    long match = Math.round(clamp(eligible ? affinity + 12 : affinity * .5));
    double competition = sch.applicants() / Math.max(sch.seats(), 1);
    long win =
        Math.round(
            clamp(eligible ? match / (1 + Math.log10(Math.max(competition, 1))) : match * .15));
    if (eligible && improvements.isEmpty()) {
      if (s.sopQuality() < 75)
        improvements.add("Strengthen your SOP — specificity about goals raises reviewer scores");
      if (s.certifications() < 3)
        improvements.add(
            "Add "
                + number(3 - s.certifications())
                + " recognized certification(s) in "
                + s.field());
      if (s.volunteerHours() < 100)
        improvements.add("Log more community service hours to stand out on holistic review");
    }
    return new Match(
        sch,
        eligible,
        match,
        win,
        missing,
        reasons,
        improvements,
        "Score computed from a fixed, auditable weight model. Gender, category and disability"
            + " fields are used only for inclusion preferences, never as penalties.");
  }

  public List<Match> rank(StudentProfile student, List<Scholarship> scholarships) {
    return scholarships.stream()
        .map(s -> match(student, s))
        .sorted(Comparator.comparingLong(Match::matchScore).reversed())
        .toList();
  }
}
