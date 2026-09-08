package com.scholarai.aid.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarai.aid.domain.*;
import com.scholarai.aid.service.FinancialAidService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AidHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final ObjectMapper json;
    private final FinancialAidService service;
    private final SignedRequestAuthenticator auth;
    public AidHttpServer(int port, ObjectMapper json, FinancialAidService service, SignedRequestAuthenticator auth) throws IOException {
        this.json = json; this.service = service; this.auth = auth;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        server.setExecutor(executor); server.createContext("/", this::handle);
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/health") && method.equals("GET")) { respond(exchange, 200, Map.of("ok", true, "service", "scholarai-java-aid")); return; }
            byte[] body = exchange.getRequestBody().readNBytes(131073);
            if (body.length > 131072) throw new AidException(413, "Request too large.");
            Principal principal = auth.authenticate(exchange, body);
            Object data;
            int status = 200;
            if (path.equals("/api/aid/programs") && method.equals("GET")) data = service.programs();
            else if (path.equals("/api/aid/assess") && method.equals("POST")) data = service.assess(json.readValue(body, FinancialNeed.class));
            else if (path.equals("/api/aid/applications") && method.equals("GET")) data = service.list(principal);
            else if (path.equals("/api/aid/applications") && method.equals("POST")) { data = service.submit(principal, json.readValue(body, FinancialAidService.Submission.class)); status = 201; }
            else if (path.matches("/api/aid/applications/[0-9a-fA-F-]{36}/transition") && method.equals("POST")) data = service.transition(principal, path.split("/")[4], json.readValue(body, FinancialAidService.Decision.class));
            else throw new AidException(404, "Endpoint not found.");
            respond(exchange, status, Map.of("ok", true, "data", data));
        } catch (AidException e) { respond(exchange, e.status(), Map.of("ok", false, "error", e.getMessage())); }
        catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) { respond(exchange, 400, Map.of("ok", false, "error", "Invalid request. Check your fields and use whole rupee amounts.")); }
        catch (Exception e) { System.err.println("Aid request failed: " + e.getClass().getSimpleName()); respond(exchange, 503, Map.of("ok", false, "error", "Financial aid storage is unavailable. Try again later.")); }
        finally { exchange.close(); }
    }
    private void respond(HttpExchange exchange, int status, Object result) throws IOException {
        byte[] bytes = json.writeValueAsBytes(result);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
