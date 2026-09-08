package com.scholarai.aid.repository;

import com.scholarai.aid.domain.AidRecord;
import java.util.List;
import java.util.Optional;

public interface AidRepository {
    List<AidRecord> list(String studentId);
    Optional<AidRecord> find(String id);
    void create(AidRecord record);
    void update(AidRecord next, long expectedVersion);
}
