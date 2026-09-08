package com.scholarai.aid;

import com.scholarai.aid.domain.Principal;
import com.scholarai.aid.http.*;
import com.scholarai.aid.repository.JdbcAidRepository;
import com.scholarai.aid.service.FinancialAidService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpBoundaryTest {
    @TempDir Path directory;
    private AidHttpServer server;
    private final String secret = "test-only-secret-at-least-32-characters-long";
    private final HttpClient client = HttpClient.newHttpClient();
    private final Principal student = new Principal("student", "Test Student", "student", false);
    @BeforeEach void setup() throws Exception { var json = Main.json(); server = new AidHttpServer(0, json, new FinancialAidService(new JdbcAidRepository("jdbc:h2:file:" + directory.resolve("aid"), json)), new SignedRequestAuthenticator(secret, json, false)); server.start(); }
    @AfterEach void stop() { server.close(); }
    private URI url(String path) { return URI.create("http://127.0.0.1:" + server.port() + path); }
    private HttpRequest signed(String path, String method, String signedBody, String sentBody, Principal principal, long timestamp) throws Exception {
        String identity = Base64.getUrlEncoder().withoutPadding().encodeToString(Main.json().writeValueAsBytes(principal));
        String nonce = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signedBody.getBytes(StandardCharsets.UTF_8)));
        String payload = timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + identity + "\n" + hash;
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HttpRequest.newBuilder(url(path)).header("X-Aid-Principal", identity).header("X-Aid-Time", Long.toString(timestamp)).header("X-Aid-Nonce", nonce).header("X-Aid-Signature", HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))).method(method, HttpRequest.BodyPublishers.ofString(sentBody)).build();
    }
    private HttpResponse<String> send(HttpRequest request) throws Exception { return client.send(request, HttpResponse.BodyHandlers.ofString()); }
    @Test void healthWorksAndUnsignedDataIsRejected() throws Exception { assertEquals(200, send(HttpRequest.newBuilder(url("/health")).build()).statusCode()); assertEquals(401, send(HttpRequest.newBuilder(url("/api/aid/applications")).build()).statusCode()); }
    @Test void acceptsSignedReadButRejectsReplay() throws Exception { var request = signed("/api/aid/programs", "GET", "", "", student, Instant.now().getEpochSecond()); var response = send(request); assertEquals(200, response.statusCode()); assertTrue(response.body().contains("need-grant")); assertEquals(401, send(request).statusCode()); }
    @Test void rejectsTamperedBodyAndExpiredSignature() throws Exception { assertEquals(401, send(signed("/api/aid/assess", "POST", "{}", "{\"tuition\":1}", student, Instant.now().getEpochSecond())).statusCode()); assertEquals(401, send(signed("/api/aid/programs", "GET", "", "", student, 0)).statusCode()); }
    @Test void liveConfigurationRejectsDemoIdentity() throws Exception { assertEquals(401, send(signed("/api/aid/programs", "GET", "", "", new Principal("student", "Demo", "student", true), Instant.now().getEpochSecond())).statusCode()); }
    @Test void javaReturnsFundingAssessment() throws Exception { String body = Main.json().writeValueAsString(DomainTest.need()); var response = send(signed("/api/aid/assess", "POST", body, body, student, Instant.now().getEpochSecond())); assertEquals(200, response.statusCode()); assertEquals(130000, Main.json().readTree(response.body()).at("/data/gap").asLong()); }
    @Test void rejectsFractionalAndMissingFinancialFields() throws Exception { for (String body : List.of("{}", "{\"annualIncome\":100,\"tuition\":1.5,\"livingCosts\":0,\"existingSupport\":0,\"contribution\":0,\"emergency\":false}")) assertEquals(400, send(signed("/api/aid/assess", "POST", body, body, student, Instant.now().getEpochSecond())).statusCode()); }
    @Test void submissionAndReviewUseHttpAuthorization() throws Exception {
        String body = Main.json().writeValueAsString(new FinancialAidService.Submission("need-grant", 80000, "Tuition assistance requested", DomainTest.need()));
        var submitted = send(signed("/api/aid/applications", "POST", body, body, student, Instant.now().getEpochSecond()));
        assertEquals(201, submitted.statusCode());
        String id = Main.json().readTree(submitted.body()).at("/data/id").asText();
        String review = "{\"status\":\"under_review\",\"note\":\"Review started\",\"amount\":0,\"expectedVersion\":1}";
        assertEquals(403, send(signed("/api/aid/applications/" + id + "/transition", "POST", review, review, student, Instant.now().getEpochSecond())).statusCode());
        assertEquals(200, send(signed("/api/aid/applications/" + id + "/transition", "POST", review, review, new Principal("admin", "Admin", "admin", false), Instant.now().getEpochSecond())).statusCode());
    }
}
