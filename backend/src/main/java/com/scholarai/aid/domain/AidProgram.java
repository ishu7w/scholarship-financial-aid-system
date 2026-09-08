package com.scholarai.aid.domain;

/** Abstraction + template method: subclasses override eligibility, never the award cap. */
public abstract class AidProgram {
    private final String id;
    private final String name;
    private final String category;
    private final long maximum;
    private final String description;

    protected AidProgram(String id, String name, String category, long maximum, String description) {
        this.id = id; this.name = name; this.category = category; this.maximum = maximum; this.description = description;
    }
    protected abstract String exclusion(FinancialNeed need);
    public final Assessment assess(FinancialNeed need) {
        String reason = exclusion(need);
        if (reason == null && need.fundingGap() == 0) reason = "Your declared education costs are already covered.";
        return new Assessment(id, reason == null, reason, reason == null ? Math.min(maximum, need.fundingGap()) : 0);
    }
    public final String id() { return id; }
    public final ProgramDetails details() { return new ProgramDetails(id, name, category, maximum, description); }
    public record Assessment(String programId, boolean eligible, String reason, long estimate) {}
    public record ProgramDetails(String id, String name, String category, long maximum, String description) {}
}
