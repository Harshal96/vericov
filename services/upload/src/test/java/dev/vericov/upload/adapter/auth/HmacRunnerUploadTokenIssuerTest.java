package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import jakarta.json.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacRunnerUploadTokenIssuerTest {
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID API_KEY_ID = UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b");

    @Test
    void issuesHs256RunnerUploadTokenWithRepositoryClaims() {
        HmacRunnerUploadTokenIssuer issuer = issuer();

        var token = issuer.issue(
                principal(),
                REPOSITORY_ID,
                "main",
                Duration.ofMinutes(15));

        assertEquals(NOW.plus(Duration.ofMinutes(15)), token.expiresAt());
        JsonObject payload = UploadJwtSupport.verifiedHs256Payload(
                token.token(),
                "test-secret",
                "vericov-upload",
                "vericov-runner-upload",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals("repository:" + REPOSITORY_ID, UploadJwtSupport.stringClaim(payload, "sub"));
        assertEquals(TENANT_ID, UploadJwtSupport.uuidClaim(payload, "tenant_id"));
        assertEquals(REPOSITORY_ID, UploadJwtSupport.uuidClaim(payload, "repository_id"));
        assertEquals(API_KEY_ID.toString(), UploadJwtSupport.stringClaim(payload, "api_key_id"));
        assertEquals("main", UploadJwtSupport.stringClaim(payload, "branch"));
        assertEquals(
                Set.of("uploads:create", "uploads:read"),
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "scopes", List.of())));
        assertEquals(
                Set.of("main", "release/*"),
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "branch_allow_patterns", List.of())));
    }

    @Test
    void rejectsTamperedTokens() {
        HmacRunnerUploadTokenIssuer issuer = issuer();
        var token = issuer.issue(principal(), REPOSITORY_ID, "main", Duration.ofMinutes(15));
        String[] parts = token.token().split("\\.");
        String signature = parts[2];
        String tamperedSignature = (signature.charAt(0) == 'A' ? 'B' : 'A') + signature.substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> UploadJwtSupport.verifiedHs256Payload(
                        tampered,
                        "test-secret",
                        "vericov-upload",
                        "vericov-runner-upload",
                        Clock.fixed(NOW, ZoneOffset.UTC)));

        assertEquals("unauthorized", exception.code());
    }

    @Test
    void rejectsExpiredTokens() {
        HmacRunnerUploadTokenIssuer issuer = issuer();
        var token = issuer.issue(principal(), REPOSITORY_ID, "main", Duration.ZERO);

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> UploadJwtSupport.verifiedHs256Payload(
                        token.token(),
                        "test-secret",
                        "vericov-upload",
                        "vericov-runner-upload",
                        Clock.fixed(NOW, ZoneOffset.UTC)));

        assertEquals("unauthorized", exception.code());
    }

    @Test
    void requiresSigningSecret() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new HmacRunnerUploadTokenIssuer(
                        " ",
                        "vericov-upload",
                        "vericov-runner-upload",
                        Clock.fixed(NOW, ZoneOffset.UTC)));

        assertTrue(exception.getMessage().contains("secret"));
    }

    private static HmacRunnerUploadTokenIssuer issuer() {
        return new HmacRunnerUploadTokenIssuer(
                "test-secret",
                "vericov-upload",
                "vericov-runner-upload",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RepositoryApiKeyPrincipal principal() {
        return new RepositoryApiKeyPrincipal(
                TENANT_ID,
                REPOSITORY_ID,
                API_KEY_ID,
                Set.of("uploads:create", "uploads:read"),
                Set.of("main", "release/*"));
    }
}
