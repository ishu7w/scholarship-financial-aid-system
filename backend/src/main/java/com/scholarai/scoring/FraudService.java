package com.scholarai.scoring;

import com.scholarai.student.StudentProfile;
import java.util.*;

public final class FraudService {
  public record Signal(String code, String severity, String message) {}

  public record Report(int riskScore, String level, List<Signal> signals) {}

  public Report assess(StudentProfile s) {
    List<Signal> signals = new ArrayList<>();
    if (s.cgpa() > 9.5 && s.attendance() < 60)
      signals.add(
          new Signal(
              "GPA_ATTENDANCE_MISMATCH",
              "high",
              "Near-perfect CGPA with very low attendance is statistically anomalous"));
    if (s.familyIncome() < 8000 && s.certifications() > 8)
      signals.add(
          new Signal(
              "INCOME_SPEND_MISMATCH",
              "medium",
              "Declared income unusually low relative to paid certification volume"));
    if (s.researchPapers() > 4 && s.year() <= 2)
      signals.add(
          new Signal(
              "EARLY_RESEARCH_VOLUME",
              "medium",
              "Publication count is atypical for academic year — verify authorship"));
    if (s.previousScholarships() > 3)
      signals.add(
          new Signal(
              "STACKED_AWARDS",
              "low",
              "Multiple concurrent scholarships — check double-funding rules"));
    if (s.behaviourScore() < 40)
      signals.add(
          new Signal("CONDUCT_FLAG", "medium", "Institution conduct score below review threshold"));
    int risk =
        Math.min(
            100,
            signals.stream()
                .mapToInt(
                    x -> x.severity().equals("high") ? 45 : x.severity().equals("medium") ? 25 : 10)
                .sum());
    return new Report(risk, risk >= 60 ? "flagged" : risk >= 25 ? "review" : "clear", signals);
  }
}
