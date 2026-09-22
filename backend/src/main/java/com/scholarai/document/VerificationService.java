package com.scholarai.document;

import static com.scholarai.scoring.ScoreService.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.scholarai.aid.domain.AidException;
import java.util.*;

public final class VerificationService {
  private final ObjectMapper json;
  private final DocumentParser parser = new DocumentParser();

  public VerificationService(ObjectMapper json) {
    this.json = json;
  }

  public record Outcome(
      String status, List<String> flags, ObjectNode extractedFields, String text) {}

  private Outcome result(String status, String flag, ObjectNode fields, String text) {
    return new Outcome(status, flag == null ? List.of() : List.of(flag), fields, text);
  }

  private String money(double value) {
    return number(Math.round(value));
  }

  public Outcome verify(String kind, String mimeType, int byteSize, String text, JsonNode profile) {
    if (!Set.of("income_cert", "marksheet", "id", "resume").contains(kind))
      throw new AidException(400, "Invalid document kind");
    ObjectNode fields =
        json.createObjectNode()
            .put("mimeType", mimeType)
            .put("byteSize", byteSize)
            .put("characters", text.length())
            .put("words", text.isBlank() ? 0 : text.trim().split("\\s+").length)
            .put("textExtracted", !text.isEmpty());
    if (text.isEmpty())
      return result(
          "pending",
          "No machine-readable text could be extracted from this file, so nothing has been verified"
              + " — re-upload a text-based PDF, or an OCR pass is required.",
          fields,
          text);
    if (kind.equals("resume")) return result("verified", null, fields, text);
    if (kind.equals("income_cert")) {
      var candidates = parser.amounts(text);
      var picked = parser.income(candidates);
      JsonNode declared = profile.path("familyIncome");
      double income = declared.asDouble();
      fields.set("incomeFound", json.valueToTree(picked == null ? null : picked.value()));
      fields.set("incomeRaw", json.valueToTree(picked == null ? null : picked.raw()));
      fields.set("incomeSourceLine", json.valueToTree(picked == null ? null : picked.line()));
      fields.set("declaredIncome", declared.isMissingNode() ? json.nullNode() : declared);
      fields.set(
          "amountCandidates",
          json.valueToTree(
              candidates.stream().limit(8).map(DocumentParser.Amount::value).toList()));
      if (picked == null)
        return result(
            "flagged",
            "No income amount could be parsed from this certificate, so it could not be checked"
                + " against the income on your profile.",
            fields,
            text);
      if (income <= 0)
        return result(
            "flagged",
            "An income of "
                + money(picked.value())
                + " was read from this certificate, but your profile has no declared family income"
                + " to compare it against.",
            fields,
            text);
      double diff = Math.abs(picked.value() - income), share = diff / income;
      if (share > .2)
        return result(
            "flagged",
            "Income certificate states "
                + money(picked.value())
                + " but your profile declares "
                + money(income)
                + " — "
                + money(diff)
                + " "
                + (picked.value() > income ? "higher" : "lower")
                + ", a "
                + Math.round(share * 100)
                + "% difference (tolerance is 20%).",
            fields,
            text);
      fields.put("differencePercent", Math.round(share * 100));
      return result("verified", null, fields, text);
    }
    if (kind.equals("marksheet")) {
      var picked = parser.academic(parser.scores(text));
      JsonNode declared = profile.path("cgpa");
      double cgpa = declared.asDouble();
      Double found = picked == null ? null : Double.valueOf(fixed(picked.normalized(), 2));
      fields.set("cgpaFound", json.valueToTree(found));
      fields.set("cgpaRaw", json.valueToTree(picked == null ? null : picked.raw()));
      fields.set("cgpaBasis", json.valueToTree(picked == null ? null : picked.basis()));
      fields.set(
          "cgpaScaleAssumption", json.valueToTree(picked == null ? null : picked.assumedScale()));
      fields.set("cgpaSourceLine", json.valueToTree(picked == null ? null : picked.line()));
      fields.set("declaredCgpa", declared.isMissingNode() ? json.nullNode() : declared);
      if (picked == null)
        return result(
            "flagged",
            "No CGPA or percentage could be parsed from this marksheet, so it could not be checked"
                + " against the CGPA on your profile.",
            fields,
            text);
      String scale = picked.assumedScale() == null ? "" : " (" + picked.assumedScale() + ")";
      if (cgpa <= 0)
        return result(
            "flagged",
            "A result of "
                + fixed(found, 2)
                + "/10 was read from \""
                + picked.raw()
                + "\""
                + scale
                + ", but your profile has no declared CGPA to compare it against.",
            fields,
            text);
      double diff = Math.abs(found - cgpa);
      if (diff > .2)
        return result(
            "flagged",
            "Marksheet shows "
                + fixed(found, 2)
                + "/10 (read from \""
                + picked.raw()
                + "\""
                + scale
                + ") but your profile declares "
                + fixed(cgpa, 2)
                + "/10 — "
                + fixed(diff, 2)
                + " points "
                + (found > cgpa ? "higher" : "lower")
                + " (tolerance is 0.20).",
            fields,
            text);
      fields.put("differencePoints", Double.parseDouble(fixed(diff, 2)));
      return result("verified", null, fields, text);
    }
    String name = profile.path("name").asText();
    var matched = parser.name(text, name);
    fields.put("nameSearched", name);
    fields.set("nameTokensMatched", json.valueToTree(matched.matched()));
    fields.set("nameTokensMissing", json.valueToTree(matched.missing()));
    if (matched.tokens().isEmpty())
      return result(
          "flagged",
          "Your profile name (\""
              + name
              + "\") has no name part long enough to search for, so this ID could not be matched.",
          fields,
          text);
    if (!matched.missing().isEmpty())
      return result(
          "flagged",
          "The name on your profile (\""
              + name
              + "\") was not fully found in this ID: "
              + matched.missing().size()
              + " of "
              + matched.tokens().size()
              + " name parts are missing ("
              + String.join(", ", matched.missing())
              + ").",
          fields,
          text);
    return result("verified", null, fields, text);
  }
}
