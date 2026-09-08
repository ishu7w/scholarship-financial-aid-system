package com.scholarai.aid.domain;

import java.util.List;

public record AidRecord(String id, String studentId, String studentName, String programId,
                        long requested, long awarded, AidStatus status, String reason,
                        FinancialNeed need, String createdAt, String updatedAt, long version,
                        List<HistoryEntry> history) {
    public AidRecord { history = List.copyOf(history); }
    public record HistoryEntry(AidStatus status, String note, String at, String actor) {}
}
