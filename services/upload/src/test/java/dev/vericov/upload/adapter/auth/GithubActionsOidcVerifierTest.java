package dev.vericov.upload.adapter.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import dev.vericov.upload.application.InvalidUploadException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GithubActionsOidcVerifierTest {
    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");
    private static final String ISSUER = "https://token.actions.githubusercontent.com";
    private static final String AUDIENCE = "vericov";

    @Test
    void verifiesRsaTokenAndCachesTheJwksKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, jwks(keyPair, "key-1"), requests);
        try {
            GithubActionsOidcVerifier verifier = verifier(server);
            String token = token(keyPair, "key-1", "RS256", validPayload());

            var first = verifier.verify(token, ISSUER, AUDIENCE);
            var second = verifier.verify(token, ISSUER, AUDIENCE);

            assertEquals("repo:acme/api:ref:refs/heads/main", UploadJwtSupport.stringClaim(first, "sub"));
            assertEquals(first, second);
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsupportedHeadersUnknownKeysAndBadSignatures() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer server = server(200, jwks(keyPair, "key-1"), new AtomicInteger());
        try {
            GithubActionsOidcVerifier verifier = verifier(server);

            assertUnauthorized(() -> verifier.verify(
                    token(keyPair, "key-1", "HS256", validPayload()), ISSUER, AUDIENCE));
            assertUnauthorized(() -> verifier.verify(
                    token(keyPair, null, "RS256", validPayload()), ISSUER, AUDIENCE));
            assertUnauthorized(() -> verifier.verify(
                    token(keyPair, "unknown", "RS256", validPayload()), ISSUER, AUDIENCE));

            String valid = token(keyPair, "key-1", "RS256", validPayload());
            String[] parts = valid.split("\\.");
            String badSignature = parts[2].substring(0, parts[2].length() - 2) + "AA";
            assertUnauthorized(() -> verifier.verify(
                    parts[0] + "." + parts[1] + "." + badSignature, ISSUER, AUDIENCE));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsFailedOrMalformedJwksResponses() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer failed = server(503, "unavailable", new AtomicInteger());
        try {
            assertUnauthorized(() -> verifier(failed).verify(
                    token(keyPair, "key-1", "RS256", validPayload()), ISSUER, AUDIENCE));
        } finally {
            failed.stop(0);
        }

        HttpServer malformed = server(200, "{}", new AtomicInteger());
        try {
            assertUnauthorized(() -> verifier(malformed).verify(
                    token(keyPair, "key-1", "RS256", validPayload()), ISSUER, AUDIENCE));
        } finally {
            malformed.stop(0);
        }
    }

    @Test
    void rejectsExpiredRegisteredClaimsAfterSignatureVerification() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpServer server = server(200, jwks(keyPair, "key-1"), new AtomicInteger());
        try {
            String expiredPayload = validPayload().replace("1780748100", "1780747199");
            assertUnauthorized(() -> verifier(server).verify(
                    token(keyPair, "key-1", "RS256", expiredPayload), ISSUER, AUDIENCE));
        } finally {
            server.stop(0);
        }
    }

    private static GithubActionsOidcVerifier verifier(HttpServer server) {
        return new GithubActionsOidcVerifier(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static HttpServer server(int status, String body, AtomicInteger requests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(KeyPair keyPair, String keyId) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return """
                {"keys":[{"kid":"%s","kty":"RSA","n":"%s","e":"%s"}]}
                """.formatted(
                keyId,
                unsigned(publicKey.getModulus()),
                unsigned(publicKey.getPublicExponent()));
    }

    private static String token(KeyPair keyPair, String keyId, String algorithm, String payload) throws Exception {
        String kid = keyId == null ? "" : ",\"kid\":\"" + keyId + "\"";
        String header = encode("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"" + kid + "}");
        String encodedPayload = encode(payload);
        String signingInput = header + "." + encodedPayload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String validPayload() {
        return """
                {
                  "iss":"https://token.actions.githubusercontent.com",
                  "aud":"vericov",
                  "exp":1780748100,
                  "sub":"repo:acme/api:ref:refs/heads/main",
                  "repository":"acme/api",
                  "ref":"refs/heads/main"
                }
                """;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.replaceAll("\\s+", "").getBytes(StandardCharsets.UTF_8));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    private static void assertUnauthorized(ThrowingRunnable runnable) {
        InvalidUploadException exception = assertThrows(InvalidUploadException.class, runnable::run);
        assertEquals("unauthorized", exception.code());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
