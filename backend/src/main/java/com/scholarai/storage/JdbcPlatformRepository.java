package com.scholarai.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.function.Function;

/** A single transactional classroom dataset, stored locally in the existing H2 database. */
public final class JdbcPlatformRepository implements PlatformRepository {
  private final String url;
  private final ObjectMapper json;

  public JdbcPlatformRepository(String url, ObjectMapper json, ObjectNode seed) throws Exception {
    this.url = url;
    this.json = json;
    try (var connection = DriverManager.getConnection(url);
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE IF NOT EXISTS platform_state (id INT PRIMARY KEY, payload CLOB NOT NULL)");
      try (var insert =
          connection.prepareStatement(
              "INSERT INTO platform_state SELECT 1, ? WHERE NOT EXISTS (SELECT 1 FROM"
                  + " platform_state WHERE id=1)")) {
        insert.setString(1, json.writeValueAsString(seed));
        insert.executeUpdate();
      }
    }
  }

  public synchronized ObjectNode read() {
    try (var c = DriverManager.getConnection(url);
        var s = c.createStatement();
        var rows = s.executeQuery("SELECT payload FROM platform_state WHERE id=1")) {
      if (!rows.next()) throw new IllegalStateException("Missing platform dataset");
      return (ObjectNode) json.readTree(rows.getString(1));
    } catch (Exception e) {
      throw new IllegalStateException("Could not read platform data", e);
    }
  }

  public synchronized <T> T update(Function<ObjectNode, T> operation) {
    try (var c = DriverManager.getConnection(url)) {
      c.setAutoCommit(false);
      try (var select = c.createStatement();
          var rows =
              select.executeQuery("SELECT payload FROM platform_state WHERE id=1 FOR UPDATE")) {
        if (!rows.next()) throw new IllegalStateException("Missing platform dataset");
        ObjectNode state = (ObjectNode) json.readTree(rows.getString(1));
        T result = operation.apply(state);
        try (var save = c.prepareStatement("UPDATE platform_state SET payload=? WHERE id=1")) {
          save.setString(1, json.writeValueAsString(state));
          save.executeUpdate();
        }
        c.commit();
        return result;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Could not save platform data", e);
    }
  }
}
