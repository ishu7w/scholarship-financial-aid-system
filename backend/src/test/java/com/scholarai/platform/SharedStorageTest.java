package com.scholarai.platform;

import static org.junit.jupiter.api.Assertions.*;
import com.scholarai.Main;
import com.scholarai.storage.RemotePlatformRepository;
import com.scholarai.aid.domain.AidException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class SharedStorageTest {
  @Test void sharedAidPreservesDuplicateAndVersionChecks() throws Exception {
    var json = Main.json();
    var seed = json.createObjectNode();
    seed.putArray("applications");
    var store = new com.scholarai.storage.JdbcPlatformRepository("jdbc:h2:mem:shared-aid-test;DB_CLOSE_DELAY=-1", json, seed);
    var repository = new com.scholarai.aid.repository.SharedAidRepository(store, json);
    var service = new com.scholarai.aid.service.FinancialAidService(repository);
    var student = new com.scholarai.aid.domain.Principal("sample-student", "Student", "student", true);
    var need = new com.scholarai.aid.domain.FinancialNeed(300000, 120000, 60000, 30000, 20000, false);
    var submission = new com.scholarai.aid.service.FinancialAidService.Submission(service.programs().get(0).id(), 50000, "Sample support for tuition and study expenses", need);
    var record = service.submit(student, submission);
    assertEquals(1, repository.list(student.id()).size());
    assertEquals(0, repository.list("other-student").size());
    assertEquals(409, assertThrows(AidException.class, () -> service.submit(student, submission)).status());
    var decision = new com.scholarai.aid.service.FinancialAidService.Decision(com.scholarai.aid.domain.AidStatus.under_review, "Review sample documents", 0, record.version());
    var updated = service.transition(student, record.id(), decision);
    assertEquals(updated, repository.find(record.id()).orElseThrow());
    assertEquals(409, assertThrows(AidException.class, () -> repository.update(updated, record.version())).status());
  }

  @Test void separateContainersShareDataAndRetryConcurrentWrites() throws Exception {
    var json = Main.json();
    var state = new AtomicReference<>(json.createObjectNode().put("count", 0));
    var version = new AtomicLong(1);
    var conflict = new AtomicBoolean(true);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/store", exchange -> {
      int status = 200;
      var result = json.createObjectNode();
      try {
        if (!"private-test-key".equals(exchange.getRequestHeaders().getFirst("X-Demo-Storage-Key"))) status = 401;
        else {
          var input = json.readTree(exchange.getRequestBody());
          if (input.path("operation").asText().equals("read")) {
            result.put("version", version.get()).set("payload", state.get());
          } else if (conflict.getAndSet(false) || input.path("version").asLong() != version.get()) {
            status = 409;
            state.set(json.createObjectNode().put("count", 5));
            version.incrementAndGet();
          } else {
            state.set((com.fasterxml.jackson.databind.node.ObjectNode) input.get("payload"));
            version.incrementAndGet();
            result.put("ok", true);
          }
        }
        byte[] bytes = json.writeValueAsBytes(result);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
      } finally { exchange.close(); }
    });
    server.start();
    try {
      String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/store";
      var first = new RemotePlatformRepository(json, endpoint, "private-test-key", "scholarships", json.createObjectNode());
      var second = new RemotePlatformRepository(json, endpoint, "private-test-key", "scholarships", json.createObjectNode());
      int count = first.update(data -> { int next = data.path("count").asInt() + 1; data.put("count", next); return next; });
      assertEquals(6, count);
      assertEquals(6, second.read().path("count").asInt());
      var unauthorized = new RemotePlatformRepository(json, endpoint, "wrong", "scholarships", json.createObjectNode());
      assertEquals(503, assertThrows(AidException.class, unauthorized::read).status());
      assertThrows(AidException.class, () -> first.update(data -> { data.put("count", 999); throw new AidException("Invalid input"); }));
      assertEquals(6, second.read().path("count").asInt());
    } finally { server.stop(0); }
  }
}
