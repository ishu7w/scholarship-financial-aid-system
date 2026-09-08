package com.scholarai.aid.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Encapsulation: only this object can create a valid application state transition. */
public final class AidApplication {
    private AidRecord record;
    public AidApplication(AidRecord record) { this.record = record; }
    public static AidApplication submit(String studentId, String name, String programId, long requested, String reason, FinancialNeed need) {
        String at = Instant.now().toString();
        return new AidApplication(new AidRecord(UUID.randomUUID().toString(), studentId, name, programId, requested, 0, AidStatus.submitted, reason, need, at, at, 1,
                List.of(new AidRecord.HistoryEntry(AidStatus.submitted, "Application submitted for verification.", at, name))));
    }
    public AidRecord transition(AidStatus next, String actor, String note, long amount) {
        EnumSet<AidStatus> allowed = switch (record.status()) {
            case submitted -> EnumSet.of(AidStatus.under_review, AidStatus.withdrawn);
            case under_review -> EnumSet.of(AidStatus.approved, AidStatus.rejected, AidStatus.withdrawn);
            case approved -> EnumSet.of(AidStatus.disbursed);
            default -> EnumSet.noneOf(AidStatus.class);
        };
        if (!allowed.contains(next)) throw new AidException(409, "This status change is no longer available. Refresh and try again.");
        if (note == null || note.trim().length() < 5 || note.trim().length() > 1000) throw new AidException("Provide a note between 5 and 1000 characters.");
        if (next == AidStatus.approved && (amount <= 0 || amount > record.requested())) throw new AidException("Award must be greater than zero and no more than the requested amount.");
        String at = Instant.now().toString();
        var history = new ArrayList<>(record.history());
        history.add(new AidRecord.HistoryEntry(next, note.trim(), at, actor));
        record = new AidRecord(record.id(), record.studentId(), record.studentName(), record.programId(), record.requested(), next == AidStatus.approved ? amount : record.awarded(), next, record.reason(), record.need(), record.createdAt(), at, record.version() + 1, history);
        return record;
    }
    public AidRecord snapshot() { return record; }
}
