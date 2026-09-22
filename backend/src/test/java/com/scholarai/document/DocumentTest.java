package com.scholarai.document;

import static org.junit.jupiter.api.Assertions.*;

import com.scholarai.Main;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentTest {
  private final DocumentParser parser = new DocumentParser();

  @Test
  void parsesIndianCurrencyFormatsAndKeepsSource() {
    String[] inputs = {
      "Annual income: ₹4,50,000",
      "Total salary Rs. 2.5 lakh per annum",
      "Net worth 1.2 crore",
      "Income Rs 3 lacs",
      "Amount 40 thousand",
      "Income INR 300000",
      "Fee $12,000 annually",
      "Amount 50000",
      "Paid ₹2024"
    };
    double[] expected = {450000, 250000, 12000000, 300000, 40000, 300000, 12000, 50000, 2024};
    for (int i = 0; i < inputs.length; i++) {
      var amount = parser.amounts(inputs[i]).get(0);
      assertEquals(expected[i], amount.value());
      assertEquals(inputs[i], amount.line());
    }
    var first = parser.amounts(inputs[0]).get(0);
    assertEquals("₹4,50,000", first.raw());
    assertTrue(first.labelled());
  }

  @Test
  void rejectsSerialNumbersYearsAndMissingFigures() {
    for (String input :
        List.of(
            "Serial 42",
            "Page 3 of 4",
            "Issued in 2024",
            "Valid 1999 to 2026",
            "",
            "To whom it may concern")) assertTrue(parser.amounts(input).isEmpty(), input);
    assertNull(parser.income(List.of()));
  }

  @Test
  void prefersLargestLabelledIncomeThenLargestUnlabelled() {
    var rows = parser.amounts("Fee 99999\nAnnual income: Rs. 2.5 lakh\nOther 700000");
    assertEquals(250000, parser.income(rows).value());
    assertTrue(parser.income(rows).labelled());
    assertEquals(340000, parser.income(parser.amounts("12000\n340000\n5000")).value());
  }

  @Test
  void normalizesAcademicScalesAndRecordsAssumptions() {
    String[] inputs = {
      "CGPA: 8.7/10",
      "GPA 3.6/4",
      "CGPA 8.7",
      "CGPA: 87",
      "Percentage: 87.5%",
      "Aggregate marks 87%",
      "Result 9/10"
    };
    double[] expected = {8.7, 9, 8.7, 8.7, 8.75, 8.7, 9};
    for (int i = 0; i < inputs.length; i++)
      assertEquals(expected[i], parser.scores(inputs[i]).get(0).normalized(), 0.000001);
    assertNull(parser.scores(inputs[0]).get(0).assumedScale());
    assertTrue(parser.scores(inputs[2]).get(0).assumedScale().contains("out of 10"));
    assertTrue(parser.scores(inputs[3]).get(0).assumedScale().contains("percentage"));
    assertTrue(parser.scores("Total: 450/500").isEmpty());
    assertTrue(parser.scores("Certificate of participation").isEmpty());
  }

  @Test
  void prefersPrintedRatioThenLabelledCgpa() {
    assertEquals(
        "cgpa-ratio", parser.academic(parser.scores("Aggregate 87%\nCGPA: 8.7/10")).basis());
    assertEquals(
        9.1, parser.academic(parser.scores("Aggregate 87%\nCGPA 9.1")).normalized(), 0.000001);
    assertNull(parser.academic(List.of()));
  }

  @Test
  void matchesNameTokensWithoutHonorificsOrInitials() {
    assertEquals(
        List.of("aarya", "sharma"),
        parser.name("This certifies AARYA  SHARMA of Delhi", "Dr. A. Aarya Sharma").matched());
    assertEquals(List.of("ram", "kumar"), parser.name("", "Shri Ram Kumar").tokens());
    assertEquals(List.of("meera", "devi", "nair"), parser.name("", "MEERA-DEVI  Nair").tokens());
    assertTrue(parser.name("", "Mr. K. B.").tokens().isEmpty());
    assertEquals(
        List.of("sharma"), parser.name("This certifies Aarya Verma", "Aarya Sharma").missing());
    assertEquals(
        List.of("aarya", "sharma"), parser.name("Issued to Rohan Patel", "Aarya Sharma").missing());
  }

  @Test
  void verificationUsesStoredClaimsAndNeverVerifiesAnUnreadableScan() {
    var json = Main.json();
    var service = new VerificationService(json);
    var profile =
        json.createObjectNode()
            .put("name", "Aarya Sharma")
            .put("familyIncome", 250000)
            .put("cgpa", 8.7);
    assertEquals("pending", service.verify("id", "image/png", 100, "", profile).status());
    assertEquals(
        "verified",
        service
            .verify("income_cert", "application/pdf", 100, "Income Rs. 2.5 lakh", profile)
            .status());
    var mismatch =
        service.verify("income_cert", "application/pdf", 100, "Income Rs. 8 lakh", profile);
    assertEquals("flagged", mismatch.status());
    assertTrue(mismatch.flags().get(0).contains("250,000"));
    assertEquals(
        "verified",
        service.verify("marksheet", "application/pdf", 100, "CGPA 8.7/10", profile).status());
    assertEquals(
        "flagged",
        service.verify("marksheet", "application/pdf", 100, "CGPA 4/10", profile).status());
    assertEquals(
        "verified",
        service.verify("id", "application/pdf", 100, "Issued to Aarya Sharma", profile).status());
    assertEquals(
        "flagged",
        service.verify("id", "application/pdf", 100, "Issued to Aarya Verma", profile).status());
  }
}
