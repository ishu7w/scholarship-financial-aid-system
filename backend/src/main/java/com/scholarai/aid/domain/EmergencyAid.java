package com.scholarai.aid.domain;

public final class EmergencyAid extends AidProgram {
    public EmergencyAid() { super("emergency", "Emergency assistance", "Emergency", 30_000, "One-time support when an unexpected financial emergency interrupts your studies."); }
    @Override protected String exclusion(FinancialNeed need) { return need.emergency() ? null : "This program requires a declared financial emergency."; }
}
