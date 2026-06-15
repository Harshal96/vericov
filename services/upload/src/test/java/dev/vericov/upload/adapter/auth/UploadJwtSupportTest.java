package dev.vericov.upload.adapter.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.upload.application.InvalidUploadException;
import jakarta.json.Json;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class UploadJwtSupportTest {
    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void verifiesArrayAudienceAndReadsClaims() {
        UUID repositoryId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String token = hs256Token("""
                {
                  "iss":"issuer",
                  "aud":["other","audience"],
                  "exp":1780748100,
                  "repository_id":"11111111-1111-1111-1111-111111111111",
                  "scopes":["uploads:create","uploads:read"]
                }
                """, "secret");

        var payload = UploadJwtSupport.verifiedHs256Payload(
                token, "secret", "issuer", "audience", CLOCK);

        assertEquals(repositoryId, UploadJwtSupport.uuidClaim(payload, "repository_id"));
        assertEquals(
                List.of("uploads:create", "uploads:read"),
                UploadJwtSupport.stringArrayClaim(payload, "scopes", List.of()));
        assertEquals(
                List.of("*"),
                UploadJwtSupport.stringArrayClaim(payload, "missing", List.of("*")));
    }

    @Test
    void rejectsMissingSecretMalformedPartsAndInvalidClaims() {
        assertUnauthorized(() -> UploadJwtSupport.verifiedHs256Payload(
                "a.b.c", " ", "issuer", "audience", CLOCK));
        assertUnauthorized(() -> UploadJwtSupport.parseUnverifiedPayload("not-a-jwt"));
        assertUnauthorized(() -> UploadJwtSupport.verifiedHs256Payload(
                "not-base64.e30.signature", "secret", "issuer", "audience", CLOCK));

        var invalidUuid = UploadJwtSupport.parseUnverifiedPayload(unsignedToken("""
                {"repository_id":"not-a-uuid"}
                """));
        assertUnauthorized(() -> UploadJwtSupport.uuidClaim(invalidUuid, "repository_id"));
        assertUnauthorized(() -> UploadJwtSupport.uuidClaim(invalidUuid, "missing"));

        var invalidArray = UploadJwtSupport.parseUnverifiedPayload(unsignedToken("""
                {"scopes":"uploads:create"}
                """));
        assertUnauthorized(() -> UploadJwtSupport.stringArrayClaim(invalidArray, "scopes", List.of()));

        var mixedArray = UploadJwtSupport.parseUnverifiedPayload(unsignedToken("""
                {"scopes":["uploads:create",1]}
                """));
        assertUnauthorized(() -> UploadJwtSupport.stringArrayClaim(mixedArray, "scopes", List.of()));
    }

    @Test
    void rejectsWrongAlgorithmSignatureAndRegisteredClaims() {
        String wrongAlgorithm = token(
                """
                {"alg":"RS256","typ":"JWT"}
                """,
                """
                {"iss":"issuer","aud":"audience","exp":1780748100}
                """,
                "signature");
        assertUnauthorized(() -> UploadJwtSupport.verifiedHs256Payload(
                wrongAlgorithm, "secret", "issuer", "audience", CLOCK));

        String signed = hs256Token("""
                {"iss":"issuer","aud":"audience","exp":1780748100}
                """, "secret");
        assertUnauthorized(() -> UploadJwtSupport.verifiedHs256Payload(
                signed, "wrong-secret", "issuer", "audience", CLOCK));

        assertRegisteredClaimsRejected("""
                {"iss":"wrong","aud":"audience","exp":1780748100}
                """);
        assertRegisteredClaimsRejected("""
                {"iss":"issuer","aud":"wrong","exp":1780748100}
                """);
        assertRegisteredClaimsRejected("""
                {"iss":"issuer","aud":"audience"}
                """);
        assertRegisteredClaimsRejected("""
                {"iss":"issuer","aud":"audience","exp":1780747200}
                """);
        assertRegisteredClaimsRejected("""
                {"iss":"issuer","aud":"audience","exp":1780748100,"nbf":1780747500}
                """);
        assertRegisteredClaimsRejected("""
                {"iss":"issuer","aud":"audience","exp":"later"}
                """);
    }

    private static void assertRegisteredClaimsRejected(String payload) {
        assertUnauthorized(() -> UploadJwtSupport.validateRegisteredClaims(
                UploadJwtSupport.parseUnverifiedPayload(unsignedToken(payload)),
                "issuer",
                "audience",
                CLOCK));
    }

    private static void assertUnauthorized(Runnable runnable) {
        InvalidUploadException exception = assertThrows(InvalidUploadException.class, runnable::run);
        assertEquals("unauthorized", exception.code());
    }

    private static String unsignedToken(String payload) {
        return token("""
                {"alg":"none","typ":"JWT"}
                """, payload, "");
    }

    private static String hs256Token(String payload, String secret) {
        String header = encode("""
                {"alg":"HS256","typ":"JWT"}
                """);
        String encodedPayload = encode(payload);
        String signingInput = header + "." + encodedPayload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String token(String header, String payload, String signature) {
        return encode(header) + "." + encode(payload) + "." + signature;
    }

    private static String encode(String json) {
        try (var reader = Json.createReader(new java.io.StringReader(json))) {
            StringWriter output = new StringWriter();
            try (var writer = Json.createWriter(output)) {
                writer.writeObject(reader.readObject());
            }
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(output.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
