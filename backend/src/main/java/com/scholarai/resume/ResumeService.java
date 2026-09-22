package com.scholarai.resume;

import static com.scholarai.scoring.ScoreService.clamp;

import java.util.*;
import java.util.regex.Pattern;

public final class ResumeService {
  private static final List<String> SKILLS =
      List.of(
          "python",
          "javascript",
          "typescript",
          "react",
          "next.js",
          "node",
          "sql",
          "machine learning",
          "deep learning",
          "data analysis",
          "tensorflow",
          "pytorch",
          "aws",
          "docker",
          "kubernetes",
          "figma",
          "java",
          "c++",
          "research",
          "leadership",
          "communication",
          "excel",
          "tableau",
          "power bi",
          "nlp",
          "statistics");

  public record Analysis(
      long atsScore,
      long resumeScore,
      Map<String, List<String>> extracted,
      List<String> suggestions) {}

  private List<String> pick(List<String> lines, String... keys) {
    return lines.stream()
        .filter(
            line -> Arrays.stream(keys).anyMatch(k -> line.toLowerCase(Locale.ROOT).contains(k)))
        .limit(5)
        .toList();
  }

  public Analysis analyze(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    var lines =
        Arrays.stream(text.split("\\n+")).map(String::trim).filter(s -> s.length() > 8).toList();
    var skills = SKILLS.stream().filter(lower::contains).toList();
    Map<String, List<String>> extracted = new LinkedHashMap<>();
    extracted.put(
        "education",
        pick(
            lines,
            "b.tech",
            "bachelor",
            "master",
            "phd",
            "university",
            "college",
            "gpa",
            "cgpa",
            "school"));
    extracted.put(
        "skills",
        skills.stream()
            .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
            .toList());
    extracted.put(
        "projects",
        pick(lines, "project", "built", "developed", "created", "implemented", "designed"));
    extracted.put(
        "experience",
        pick(lines, "intern", "engineer", "analyst", "assistant", "worked", "experience"));
    extracted.put(
        "achievements",
        pick(lines, "award", "winner", "rank", "medal", "scholarship", "finalist", "hackathon"));
    extracted.put(
        "certifications",
        pick(lines, "certified", "certification", "certificate", "course", "credential"));
    boolean numbers =
        Pattern.compile(
                "\\d+%|\\d+x|\\d+ (users|students|projects|people|hours)", Pattern.CASE_INSENSITIVE)
            .matcher(text)
            .find();
    int words = text.split("\\s+", -1).length;
    long ats =
        Math.round(
            clamp(
                40
                    + Math.min(skills.size() * 4, 24)
                    + (extracted.get("education").isEmpty() ? 0 : 8)
                    + (extracted.get("experience").isEmpty() ? 0 : 8)
                    + (extracted.get("projects").isEmpty() ? 0 : 6)
                    + (numbers ? 8 : 0)
                    + (words > 150 && words < 900 ? 6 : 0)));
    long score =
        Math.round(
            clamp(
                ats * .6
                    + extracted.get("achievements").size() * 5
                    + extracted.get("certifications").size() * 4));
    List<String> suggestions = new ArrayList<>();
    if (!numbers)
      suggestions.add(
          "Quantify impact — add metrics like “improved X by 30%” or “used by 500 students”.");
    if (skills.size() < 6)
      suggestions.add(
          "List more concrete, searchable skills — ATS systems match on exact keywords.");
    if (extracted.get("achievements").isEmpty())
      suggestions.add(
          "Add an Achievements section — awards and ranks materially raise scholarship scores.");
    if (extracted.get("certifications").isEmpty())
      suggestions.add("Include certifications with issuing body and year.");
    if (words > 900)
      suggestions.add("Trim to one page — reviewers spend under 60 seconds on first pass.");
    if (words < 150)
      suggestions.add(
          "Resume is too sparse — expand project and experience descriptions with action verbs.");
    if (suggestions.isEmpty())
      suggestions.add(
          "Strong resume. Tailor the top third to each scholarship's focus area before applying.");
    return new Analysis(ats, score, extracted, suggestions);
  }
}
