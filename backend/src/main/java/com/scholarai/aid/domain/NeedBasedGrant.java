package com.scholarai.aid.domain;

public final class NeedBasedGrant extends AidProgram {
    public NeedBasedGrant() { super("need-grant", "Need-based education grant", "Need-based", 100_000, "Support for tuition and essential living costs for students with limited household income."); }
    @Override protected String exclusion(FinancialNeed need) { return need.annualIncome() > 600_000 ? "Annual household income must be ₹6,00,000 or less." : null; }
}
