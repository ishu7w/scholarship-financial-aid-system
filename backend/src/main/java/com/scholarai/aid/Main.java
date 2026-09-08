package com.scholarai.aid;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.scholarai.aid.http.*;
import com.scholarai.aid.repository.JdbcAidRepository;
import com.scholarai.aid.service.FinancialAidService;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    public static ObjectMapper json() {
        return JsonMapper.builder().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .build();
    }
    public static void main(String[] args) throws Exception {
        var env = System.getenv();
        String secret = env.get("AID_API_SECRET");
        int port = Integer.parseInt(env.getOrDefault("AID_PORT", "8080"));
        Path directory = Path.of(env.getOrDefault("AID_DATA_DIR", "data")).toAbsolutePath();
        Files.createDirectories(directory);
        ObjectMapper json = json();
        var repository = new JdbcAidRepository("jdbc:h2:file:" + directory.resolve("financial-aid") + ";DB_CLOSE_ON_EXIT=FALSE", json);
        var server = new AidHttpServer(port, json, new FinancialAidService(repository), new SignedRequestAuthenticator(secret, json, Boolean.parseBoolean(env.getOrDefault("AID_ALLOW_DEMO", "false"))));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Java financial aid backend ready on http://127.0.0.1:" + server.port());
    }
}
