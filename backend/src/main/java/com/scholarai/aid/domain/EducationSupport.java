package com.scholarai.aid.domain;

public final class EducationSupport extends AidProgram {
    public EducationSupport() { super("education-support", "Study essentials fund", "Study essentials", 50_000, "Support for accommodation, books, transport, and study equipment."); }
    @Override protected String exclusion(FinancialNeed need) { return need.annualIncome() > 1_000_000 ? "Annual household income must be ₹10,00,000 or less." : null; }
}
