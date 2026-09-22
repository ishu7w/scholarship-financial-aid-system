package com.scholarai.scoring;

import com.scholarai.student.StudentProfile;
import java.text.NumberFormat;
import java.util.*;

/** The published ten-component model. All calculations happen in Java. */
public final class ScoreService {
  public record Component(
      String key, String label, double weight, long raw, double weighted, String detail) {}

  public record Score(
      long total,
      long confidence,
      List<Component> components,
      List<String> strengths,
      List<String> gaps) {}

  public static double clamp(double value) {
    return Math.min(100, Math.max(0, value));
  }

  public static String number(double value) {
    return NumberFormat.getNumberInstance(Locale.US).format(value);
  }

  public static String fixed(double value, int digits) {
    return new java.math.BigDecimal(value)
        .setScale(digits, java.math.RoundingMode.HALF_UP)
        .toPlainString();
  }

  private Component part(String key, String label, double weight, double raw, String detail) {
    return new Component(
        key,
        label,
        weight,
        Math.round(raw),
        new java.math.BigDecimal(raw * weight)
            .setScale(1, java.math.RoundingMode.HALF_UP)
            .doubleValue(),
        detail);
  }

  public Score calculate(StudentProfile s) {
    List<Component> parts =
        List.of(
            part(
                "academic",
                "Academic Performance",
                .22,
                clamp(s.cgpa() * 10 * .8 + s.attendance() * .2),
                "CGPA "
                    + fixed(s.cgpa(), 2)
                    + "/10 (80%) + attendance "
                    + number(s.attendance())
                    + "% (20%)"),
            part(
                "financialNeed",
                "Financial Need",
                .18,
                clamp(100 - (Math.log10(Math.max(s.familyIncome(), 1000)) - 3) * 45),
                "Annual family income $"
                    + number(s.familyIncome())
                    + " mapped on log-need curve"
                    + (s.firstGeneration() ? " · first-generation student" : "")),
            part(
                "achievements",
                "Achievements",
                .12,
                clamp(s.achievements() * 14 + s.hackathons() * 8 + s.sportsLevel() * 10),
                number(s.achievements())
                    + " awards, "
                    + number(s.hackathons())
                    + " hackathons, sports level "
                    + number(s.sportsLevel())
                    + "/3"),
            part(
                "research",
                "Research Output",
                .10,
                clamp(s.researchPapers() * 25),
                number(s.researchPapers()) + " peer-reviewed publications"),
            part(
                "leadership",
                "Leadership",
                .08,
                clamp(s.leadershipRoles() * 28),
                number(s.leadershipRoles()) + " verified leadership roles"),
            part(
                "projects",
                "Projects & Skills",
                .08,
                clamp(s.projects() * 12 + s.certifications() * 8 + s.skills().size() * 4),
                number(s.projects())
                    + " projects, "
                    + number(s.certifications())
                    + " certifications, "
                    + s.skills().size()
                    + " skills"),
            part(
                "community",
                "Community Service",
                .07,
                clamp(s.volunteerHours() / 3),
                number(s.volunteerHours()) + " volunteer hours logged"),
            part(
                "sop",
                "Statement of Purpose",
                .08,
                s.sopQuality(),
                "NLP quality score "
                    + number(s.sopQuality())
                    + "/100 (clarity, specificity, intent)"),
            part(
                "recommendation",
                "Recommendations",
                .04,
                s.recommendationStrength(),
                "Aggregate recommender strength " + number(s.recommendationStrength()) + "/100"),
            part(
                "behaviour",
                "Behaviour & Integrity",
                .03,
                s.behaviourScore(),
                "Conduct score " + number(s.behaviourScore()) + "/100 from institution records"));
    var sorted =
        parts.stream().sorted(Comparator.comparingLong(Component::raw).reversed()).toList();
    var low = sorted.stream().filter(c -> c.raw() < 50).toList();
    return new Score(
        Math.round(parts.stream().mapToDouble(Component::weighted).sum()),
        Math.round(
            clamp(
                s.profileCompletion() * .7
                    + Math.min(s.projects() + s.certifications() + s.achievements(), 15) * 2)),
        parts,
        sorted.stream().limit(3).map(Component::label).toList(),
        low.stream().skip(Math.max(0, low.size() - 3)).map(Component::label).toList());
  }
}
