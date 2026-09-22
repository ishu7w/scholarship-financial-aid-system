package com.scholarai.document;

import java.util.*;
import java.util.regex.*;

/** Extracts money, academic results and names from already-extracted PDF text. */
public final class DocumentParser {
  public record Amount(double value, String raw, String line, boolean labelled) {}

  public record AcademicScore(
      double normalized, String raw, String line, String basis, String assumedScale) {}

  public record NameMatch(List<String> tokens, List<String> matched, List<String> missing) {}

  private static final Pattern AMOUNT =
      Pattern.compile(
          "(?:(₹|\\$|rs\\.?|inr|usd)\\s*)?(\\d[\\d,]*(?:\\.\\d{1,2})?)(?:\\s*(thousand|lakhs?|lacs?|crores?))?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern INCOME =
      Pattern.compile(
          "income|salary|earnings|remuneration|per annum|annually", Pattern.CASE_INSENSITIVE);
  private static final Pattern CGPA =
      Pattern.compile(
          "(?:cgpa|sgpa|gpa|cpi|grade point"
              + " average)\\s*[:=-]?\\s*(\\d{1,2}(?:\\.\\d{1,3})?)\\s*(?:/\\s*(\\d{1,2}(?:\\.\\d{1,2})?))?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern RATIO =
      Pattern.compile("(\\d{1,2}(?:\\.\\d{1,3})?)\\s*/\\s*(10|10\\.0|4|4\\.0)(?!\\d)");
  private static final Pattern PERCENT =
      Pattern.compile(
          "(?:percentage|percent|marks|aggregate|total)?\\s*[:=-]?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)\\s*%",
          Pattern.CASE_INSENSITIVE);

  public List<Amount> amounts(String text) {
    List<Amount> found = new ArrayList<>();
    for (String rawLine : text.split("\\r?\\n")) {
      String line = rawLine.trim();
      boolean labelled = INCOME.matcher(line).find();
      Matcher matcher = AMOUNT.matcher(line);
      while (matcher.find()) {
        double base = Double.parseDouble(matcher.group(2).replace(",", ""));
        if (!Double.isFinite(base) || base <= 0) continue;
        String scale = matcher.group(3);
        double multiplier =
            scale == null
                ? 1
                : scale.toLowerCase(Locale.ROOT).startsWith("thousand")
                    ? 1000
                    : scale.toLowerCase(Locale.ROOT).startsWith("crore") ? 10000000 : 100000;
        double value = base * multiplier;
        if (matcher.group(1) == null
            && scale == null
            && (value < 1000
                || (matcher.group(2).replace(",", "").length() == 4
                    && base >= 1900
                    && base <= 2100))) continue;
        found.add(new Amount(value, matcher.group().trim(), line, labelled));
      }
    }
    return found;
  }

  public Amount income(List<Amount> values) {
    var labelled = values.stream().filter(Amount::labelled).toList();
    return (labelled.isEmpty() ? values : labelled)
        .stream().max(Comparator.comparingDouble(Amount::value)).orElse(null);
  }

  public List<AcademicScore> scores(String text) {
    List<AcademicScore> found = new ArrayList<>();
    for (String rawLine : text.split("\\r?\\n")) {
      String line = rawLine.trim();
      Matcher labelled = CGPA.matcher(line);
      if (labelled.find()) {
        double value = Double.parseDouble(labelled.group(1));
        double denominator = labelled.group(2) == null ? 0 : Double.parseDouble(labelled.group(2));
        if (value > 0) {
          if (denominator > 0) {
            found.add(
                new AcademicScore(
                    value / denominator * 10, labelled.group().trim(), line, "cgpa-ratio", null));
            continue;
          }
          if (value <= 10) {
            found.add(
                new AcademicScore(
                    value,
                    labelled.group().trim(),
                    line,
                    "cgpa",
                    "out of 10 (denominator not printed)"));
            continue;
          }
          if (value <= 100) {
            found.add(
                new AcademicScore(
                    value / 10,
                    labelled.group().trim(),
                    line,
                    "percentage",
                    "read as a percentage and divided by 10"));
            continue;
          }
        }
      }
      Matcher ratio = RATIO.matcher(line);
      if (ratio.find()) {
        double value = Double.parseDouble(ratio.group(1)),
            denominator = Double.parseDouble(ratio.group(2));
        if (denominator > 0 && value <= denominator)
          found.add(
              new AcademicScore(
                  value / denominator * 10, ratio.group().trim(), line, "cgpa-ratio", null));
        continue;
      }
      Matcher percent = PERCENT.matcher(line);
      if (percent.find()) {
        double value = Double.parseDouble(percent.group(1));
        if (value > 0 && value <= 100)
          found.add(
              new AcademicScore(
                  value / 10,
                  percent.group().replaceFirst("^[\\s:—–-]+", "").trim(),
                  line,
                  "percentage",
                  "percentage divided by 10 to compare with CGPA"));
      }
    }
    return found;
  }

  public AcademicScore academic(List<AcademicScore> values) {
    return values.stream()
        .filter(s -> s.basis().equals("cgpa-ratio"))
        .findFirst()
        .orElseGet(
            () ->
                values.stream()
                    .filter(s -> s.basis().equals("cgpa"))
                    .findFirst()
                    .orElse(values.isEmpty() ? null : values.get(0)));
  }

  public NameMatch name(String text, String name) {
    Set<String> noise = Set.of("mr", "mrs", "ms", "dr", "shri", "smt", "the", "and");
    var tokens =
        Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^a-z]+"))
            .filter(t -> t.length() >= 3 && !noise.contains(t))
            .toList();
    String haystack = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return new NameMatch(
        tokens,
        tokens.stream().filter(haystack::contains).toList(),
        tokens.stream().filter(t -> !haystack.contains(t)).toList());
  }
}
