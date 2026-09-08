package com.scholarai.aid;

import com.scholarai.aid.domain.*;
import com.scholarai.aid.repository.JdbcAidRepository;
import com.scholarai.aid.service.FinancialAidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {
    @TempDir Path directory;
    private String url;
    private JdbcAidRepository repository;
    private FinancialAidService service;
    private final Principal student = new Principal("student", "Test Student", "student", false);
    private final Principal admin = new Principal("admin", "Admin", "admin", false);
    @BeforeEach void setup() { url = "jdbc:h2:file:" + directory.resolve("aid"); repository = new JdbcAidRepository(url, Main.json()); service = new FinancialAidService(repository); }
    private AidRecord submit() { return service.submit(student, new FinancialAidService.Submission("need-grant", 80000, "Tuition assistance requested", DomainTest.need())); }
    private FinancialAidService.Decision review(long version) { return new FinancialAidService.Decision(AidStatus.under_review, "Review started", 0, version); }
    @Test void persistsAcrossRepositoryRecreation() { AidRecord record = submit(); var reopened = new JdbcAidRepository(url, Main.json()); assertEquals(record, reopened.find(record.id()).orElseThrow()); }
    @Test void rejectsDuplicateProgramApplications() { submit(); assertEquals(409, assertThrows(AidException.class, this::submit).status()); }
    @Test void isolatesStudentRecords() { submit(); assertTrue(service.list(new Principal("other", "Other", "student", false)).isEmpty()); assertEquals(1, service.list(admin).size()); }
    @Test void rejectsStudentAndInstitutionReview() { var record = submit(); for (String role : new String[]{"student", "institution"}) assertEquals(403, assertThrows(AidException.class, () -> service.transition(new Principal("student", "Test", role, false), record.id(), review(1))).status()); }
    @Test void permitsAdminReview() { var record = submit(); assertEquals(AidStatus.under_review, service.transition(admin, record.id(), review(1)).status()); }
    @Test void limitsDemoReviewerToOwnRecords() { var record = submit(); assertEquals(404, assertThrows(AidException.class, () -> service.transition(new Principal("other", "Demo", "student", true), record.id(), review(1))).status()); assertEquals(AidStatus.under_review, service.transition(new Principal("student", "Demo", "student", true), record.id(), review(1)).status()); }
    @Test void protectsAgainstStaleReviews() { var record = submit(); service.transition(admin, record.id(), review(1)); assertEquals(409, assertThrows(AidException.class, () -> service.transition(admin, record.id(), new FinancialAidService.Decision(AidStatus.approved, "Verified", 50000, 1))).status()); }
    @Test void repositoryRejectsConcurrentOverwrite() { var record = submit(); var first = new AidApplication(record).transition(AidStatus.under_review, "Admin", "Review started", 0); repository.update(first, 1); var other = new AidApplication(record).transition(AidStatus.withdrawn, "Student", "Changed plan", 0); assertThrows(AidException.class, () -> repository.update(other, 1)); }
    @Test void blocksWithdrawingAnotherStudentsApplication() { var record = submit(); assertThrows(AidException.class, () -> service.transition(new Principal("other", "Other", "student", false), record.id(), new FinancialAidService.Decision(AidStatus.withdrawn, "No longer needed", 0, 1))); }
    @Test void recomputesEligibilityAndCapAtSubmission() { assertThrows(AidException.class, () -> service.submit(student, new FinancialAidService.Submission("emergency", 1000, "Emergency support requested", DomainTest.need()))); assertThrows(AidException.class, () -> service.submit(student, new FinancialAidService.Submission("need-grant", 100001, "Tuition assistance requested", DomainTest.need()))); }
}
