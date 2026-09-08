package com.scholarai.aid.domain;

/** Immutable value object. Monetary amounts are whole INR, never floating point. */
public record FinancialNeed(long annualIncome, long tuition, long livingCosts,
                            long existingSupport, long contribution, boolean emergency) {
    public FinancialNeed {
        for (long value : new long[]{annualIncome, tuition, livingCosts, existingSupport, contribution}) {
            if (value < 0 || value > 100_000_000) throw new AidException("Enter whole rupee amounts between 0 and 10,00,00,000.");
        }
    }
    public long fundingGap() { return Math.max(0, tuition + livingCosts - existingSupport - contribution); }
}
