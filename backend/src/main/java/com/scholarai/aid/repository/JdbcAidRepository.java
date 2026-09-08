package com.scholarai.aid.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarai.aid.domain.AidException;
import com.scholarai.aid.domain.AidRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation, injected through the interface; SQL parameters never contain interpolated input. */
public final class JdbcAidRepository implements AidRepository {
    private final String url;
    private final ObjectMapper json;
    public JdbcAidRepository(String url, ObjectMapper json) {
        this.url = url; this.json = json;
        try (var connection = connection(); var stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS aid_applications (id VARCHAR(36) PRIMARY KEY, student_id VARCHAR(200) NOT NULL, program_id VARCHAR(100) NOT NULL, version BIGINT NOT NULL, payload CLOB NOT NULL, UNIQUE(student_id, program_id))");
        } catch (SQLException e) { throw new IllegalStateException("Cannot initialize aid storage", e); }
    }
    private Connection connection() throws SQLException { return DriverManager.getConnection(url, "sa", ""); }
    @Override public List<AidRecord> list(String studentId) {
        try (var conn = connection(); var stmt = conn.prepareStatement("SELECT payload FROM aid_applications" + (studentId == null ? "" : " WHERE student_id = ?"))) {
            if (studentId != null) stmt.setString(1, studentId);
            var result = new ArrayList<AidRecord>();
            try (var rows = stmt.executeQuery()) { while (rows.next()) result.add(json.readValue(rows.getString(1), AidRecord.class)); }
            return List.copyOf(result);
        } catch (Exception e) { throw new IllegalStateException("Cannot read aid storage", e); }
    }
    @Override public Optional<AidRecord> find(String id) {
        try (var conn = connection(); var stmt = conn.prepareStatement("SELECT payload FROM aid_applications WHERE id = ?")) {
            stmt.setString(1, id);
            try (var rows = stmt.executeQuery()) { return rows.next() ? Optional.of(json.readValue(rows.getString(1), AidRecord.class)) : Optional.empty(); }
        } catch (Exception e) { throw new IllegalStateException("Cannot read aid storage", e); }
    }
    @Override public void create(AidRecord record) {
        try (var conn = connection(); var stmt = conn.prepareStatement("INSERT INTO aid_applications (id, student_id, program_id, version, payload) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, record.id()); stmt.setString(2, record.studentId()); stmt.setString(3, record.programId()); stmt.setLong(4, record.version()); stmt.setString(5, json.writeValueAsString(record)); stmt.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new AidException(409, "You have already applied to this program.");
            throw new IllegalStateException("Cannot save aid application", e);
        } catch (Exception e) { throw new IllegalStateException("Cannot serialize aid application", e); }
    }
    @Override public void update(AidRecord next, long expectedVersion) {
        try (var conn = connection(); var stmt = conn.prepareStatement("UPDATE aid_applications SET payload = ?, version = ? WHERE id = ? AND version = ?")) {
            stmt.setString(1, json.writeValueAsString(next)); stmt.setLong(2, next.version()); stmt.setString(3, next.id()); stmt.setLong(4, expectedVersion);
            if (stmt.executeUpdate() == 0) throw new AidException(409, "Another reviewer changed this application. Refresh and try again.");
        } catch (AidException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Cannot update aid application", e); }
    }
}
