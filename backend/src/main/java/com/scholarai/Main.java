package com.scholarai;

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
    return JsonMapper.builder()
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
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
    com.scholarai.aid.repository.AidRepository repository;
    com.scholarai.storage.PlatformRepository platformRepository;
    String remoteUrl = env.get("DEMO_STORAGE_URL"), remoteKey = env.get("DEMO_STORAGE_KEY");
    if (remoteUrl != null && remoteKey != null) {
      platformRepository = new com.scholarai.storage.RemotePlatformRepository(
          json, remoteUrl, remoteKey, "scholarships", com.scholarai.storage.DemoData.load(json));
      var aidSeed = json.createObjectNode();
      aidSeed.putArray("applications");
      repository = new com.scholarai.aid.repository.SharedAidRepository(
          new com.scholarai.storage.RemotePlatformRepository(json, remoteUrl, remoteKey, "financial-aid", aidSeed), json);
    } else {
      if (env.containsKey("VERCEL")) throw new IllegalStateException("Hosted demo requires shared storage configuration");
      repository = new JdbcAidRepository(
          "jdbc:h2:file:" + directory.resolve("financial-aid") + ";DB_CLOSE_ON_EXIT=FALSE", json);
      platformRepository = new com.scholarai.storage.JdbcPlatformRepository(
          "jdbc:h2:file:" + directory.resolve("platform") + ";DB_CLOSE_ON_EXIT=FALSE", json,
          com.scholarai.storage.DemoData.load(json));
    }
    var platform = new com.scholarai.http.PlatformApi(json, platformRepository);
    var server =
        new AidHttpServer(
            port,
            json,
            new FinancialAidService(repository),
            new SignedRequestAuthenticator(
                secret, json, Boolean.parseBoolean(env.getOrDefault("AID_ALLOW_DEMO", "false"))),
            platform);
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    server.start();
    System.out.println(
        "Java scholarship and financial aid backend ready on http://127.0.0.1:" + server.port());
  }
}
