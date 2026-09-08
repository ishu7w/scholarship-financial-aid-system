package com.scholarai.aid;

import com.scholarai.aid.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    static FinancialNeed need() { return new FinancialNeed(300000, 120000, 60000, 30000, 20000, false); }
    static AidApplication application() { return AidApplication.submit("student", "Test Student", "need-grant", 100000, "Tuition assistance requested", need()); }
    @Test void calculatesFundingGap() { assertEquals(130000, need().fundingGap()); }
    @Test void clampsCoveredGapAtZero() { assertEquals(0, new FinancialNeed(0, 1000, 0, 5000, 0, false).fundingGap()); }
    @ParameterizedTest @ValueSource(longs = {-1, 100000001, Long.MAX_VALUE}) void rejectsInvalidMoney(long amount) { assertThrows(AidException.class, () -> new FinancialNeed(0, amount, 0, 0, 0, false)); }
    @Test void dispatchesSubclassRulesPolymorphically() { List<AidProgram> programs = List.of(new NeedBasedGrant(), new EmergencyAid(), new EducationSupport()); assertEquals(List.of(true, false, true), programs.stream().map(p -> p.assess(need()).eligible()).toList()); }
    @Test void enforcesIncomeBoundary() { assertTrue(new NeedBasedGrant().assess(new FinancialNeed(600000, 1000, 0, 0, 0, false)).eligible()); assertFalse(new NeedBasedGrant().assess(new FinancialNeed(600001, 1000, 0, 0, 0, false)).eligible()); }
    @Test void emergencyRequiresDeclaration() { assertTrue(new EmergencyAid().assess(new FinancialNeed(900000, 100000, 0, 0, 0, true)).eligible()); }
    @Test void capsAtNeedAndProgramMaximum() { assertEquals(100000, new NeedBasedGrant().assess(need()).estimate()); assertEquals(1000, new NeedBasedGrant().assess(new FinancialNeed(0, 1000, 0, 0, 0, false)).estimate()); }
    @Test void deniesAlreadyFundedNeed() { assertFalse(new NeedBasedGrant().assess(new FinancialNeed(0, 1000, 0, 1000, 0, false)).eligible()); }
    @Test void preventsSkippingReview() { assertThrows(AidException.class, () -> application().transition(AidStatus.approved, "Admin", "Verified", 1000)); }
    @Test void recordsPartialAwardAndPaymentOnce() { var app = application(); app.transition(AidStatus.under_review, "Admin", "Review started", 0); app.transition(AidStatus.approved, "Admin", "Verified documents", 75000); var paid = app.transition(AidStatus.disbursed, "Admin", "DEMO-UTR-001", 0); assertEquals(75000, paid.awarded()); assertEquals(4, paid.history().size()); assertThrows(AidException.class, () -> app.transition(AidStatus.disbursed, "Admin", "Duplicate transfer", 0)); }
    @ParameterizedTest @ValueSource(longs = {0, -1, 100001}) void rejectsInvalidAwards(long amount) { var app = application(); app.transition(AidStatus.under_review, "Admin", "Review started", 0); assertThrows(AidException.class, () -> app.transition(AidStatus.approved, "Admin", "Verified", amount)); }
    @Test void immutableHistoryAndSnapshots() { var app = application(); var before = app.snapshot(); assertThrows(UnsupportedOperationException.class, () -> before.history().clear()); app.transition(AidStatus.under_review, "Admin", "Review started", 0); assertEquals(AidStatus.submitted, before.status()); assertEquals(1, before.history().size()); }
    @Test void withdrawalIsTerminal() { var app = application(); app.transition(AidStatus.withdrawn, "Student", "No longer needed", 0); assertThrows(AidException.class, () -> app.transition(AidStatus.under_review, "Admin", "Review started", 0)); }
}
