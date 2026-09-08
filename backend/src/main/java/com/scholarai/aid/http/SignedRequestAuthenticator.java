package com.scholarai.aid.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarai.aid.domain.AidException;
import com.scholarai.aid.domain.Principal;
import com.sun.net.httpserver.HttpExchange;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/** Next.js verifies the user session and signs each server-to-server request. */
public final class SignedRequestAuthenticator {
    private final byte[] secret;
    private final ObjectMapper json;
    private final boolean allowDemo;
    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();
    public SignedRequestAuthenticator(String secret, ObjectMapper json, boolean allowDemo) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("AID_API_SECRET must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8); this.json = json; this.allowDemo = allowDemo;
    }
    public Principal authenticate(HttpExchange exchange, byte[] body) {
        try {
            var headers = exchange.getRequestHeaders();
            String timestamp = headers.getFirst("X-Aid-Time");
            String nonce = headers.getFirst("X-Aid-Nonce");
            String identity = headers.getFirst("X-Aid-Principal");
            String signature = headers.getFirst("X-Aid-Signature");
            long now = Instant.now().getEpochSecond();
            long sent = Long.parseLong(timestamp);
            if (sent < now - 60 || sent > now + 60 || nonce == null || nonce.length() > 100 || identity == null || identity.length() > 4000 || signature == null) throw new IllegalArgumentException();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String payload = timestamp + "\n" + nonce + "\n" + exchange.getRequestMethod() + "\n" + exchange.getRequestURI().getRawPath() + "\n" + identity + "\n" + hash;
            Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) throw new IllegalArgumentException();
            Principal principal = json.readValue(Base64.getUrlDecoder().decode(identity), Principal.class);
            if (principal.demo() && !allowDemo) throw new IllegalArgumentException();
            nonces.entrySet().removeIf(e -> e.getValue() < now - 120);
            if (nonces.putIfAbsent(nonce, now) != null) throw new IllegalArgumentException();
            return principal;
        } catch (Exception e) { throw new AidException(401, "Invalid or expired service authentication."); }
    }
}
