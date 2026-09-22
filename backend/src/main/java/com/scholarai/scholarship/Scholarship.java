package com.scholarai.scholarship;

import java.util.List;

public record Scholarship(
    String id,
    String name,
    String provider,
    String category,
    double amount,
    String currency,
    String deadline,
    double seats,
    double applicants,
    String description,
    Criteria criteria,
    List<String> tags) {
  public record Criteria(
      double minCgpa,
      Double maxIncome,
      double minAttendance,
      boolean requiresResearch,
      boolean requiresLeadership,
      boolean womenOnly,
      boolean minorityOnly,
      boolean sportsRequired,
      boolean disabilityPreferred,
      List<String> locations,
      List<String> fields) {}
}
