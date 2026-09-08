package com.scholarai.aid.service;

import com.scholarai.aid.domain.*;
import com.scholarai.aid.repository.AidRepository;
import java.util.List;

/** Composition and dependency inversion: business operations depend on abstractions. */
public final class FinancialAidService {
    private final AidRepository repository;
    private final List<AidProgram> programs;
    public FinancialAidService(AidRepository repository) { this(repository, List.of(new NeedBasedGrant(), new EmergencyAid(), new EducationSupport())); }
    public FinancialAidService(AidRepository repository, List<AidProgram> programs) { this.repository = repository; this.programs = List.copyOf(programs); }
    public List<AidProgram.ProgramDetails> programs() { return programs.stream().map(AidProgram::details).toList(); }
    public AssessmentResult assess(FinancialNeed need) {
        if (need == null) throw new AidException("Provide a funding declaration.");
        return new AssessmentResult(need.fundingGap(), programs.stream().map(p -> p.assess(need)).toList());
    }
    public List<AidRecord> list(Principal principal) { return repository.list(principal.role().equals("admin") ? null : principal.id()); }
    public AidRecord submit(Principal principal, Submission input) {
        if (!principal.role().equals("student")) throw new AidException(403, "Only student accounts can apply.");
        if (input.need() == null) throw new AidException("Provide a funding declaration.");
        if (input.reason() == null || input.reason().trim().length() < 20 || input.reason().trim().length() > 2000) throw new AidException("Explain your need in 20–2000 characters.");
        AidProgram program = programs.stream().filter(p -> p.id().equals(input.programId())).findFirst().orElseThrow(() -> new AidException(404, "Program not found."));
        var assessment = program.assess(input.need());
        if (!assessment.eligible()) throw new AidException(assessment.reason());
        if (input.requested() <= 0 || input.requested() > assessment.estimate()) throw new AidException("Requested amount exceeds your eligible estimate or is not greater than zero.");
        var record = AidApplication.submit(principal.id(), principal.name(), input.programId(), input.requested(), input.reason().trim(), input.need()).snapshot();
        repository.create(record);
        return record;
    }
    public AidRecord transition(Principal principal, String id, Decision decision) {
        if (decision.status() == null) throw new AidException("Choose a valid status.");
        if (decision.status() != AidStatus.withdrawn && !principal.canReview()) throw new AidException(403, "Only administrators can review or record disbursements.");
        AidRecord previous = repository.find(id).orElseThrow(() -> new AidException(404, "Application not found."));
        // Demo reviewers may only inspect the demo identity's own records.
        if ((!principal.role().equals("admin") && !previous.studentId().equals(principal.id())) || (decision.status() == AidStatus.withdrawn && !previous.studentId().equals(principal.id()))) throw new AidException(404, "Application not found.");
        if (decision.expectedVersion() != previous.version()) throw new AidException(409, "Application changed. Refresh and try again.");
        var next = new AidApplication(previous).transition(decision.status(), principal.demo() && decision.status() != AidStatus.withdrawn ? "Demo reviewer" : principal.name(), decision.note(), decision.amount());
        repository.update(next, previous.version());
        return next;
    }
    public record Submission(String programId, long requested, String reason, FinancialNeed need) {}
    public record Decision(AidStatus status, String note, long amount, long expectedVersion) {}
    public record AssessmentResult(long gap, List<AidProgram.Assessment> assessments) {}
}
