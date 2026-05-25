package dev.vericov.organization.adapter.auth;

import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.domain.UserAuthContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupabaseJwtUserPrincipalResolverTest {
    private static final String SIGNING_KEY = "test-supabase-jwt-signing-key-32-bytes";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void verifiesSupabaseJwtAndExtractsSubjectAndEmail() {
        var resolver = new SupabaseJwtUserPrincipalResolver(
                SIGNING_KEY,
                "http://localhost:8000/auth/v1",
                "authenticated",
                Clock.fixed(NOW, ZoneOffset.UTC));
        String token = token("""
                {
                  "iss": "http://localhost:8000/auth/v1",
                  "aud": "authenticated",
                  "exp": 1800000000,
                  "sub": "%s",
                  "email": "USER@Example.com",
                  "role": "authenticated",
                  "user_metadata": {
                    "role": "owner"
                  }
                }
                """.formatted(USER_ID));

        var user = resolver.resolve(new UserAuthContext("Bearer " + token, null));

        assertEquals(java.util.UUID.fromString(USER_ID), user.userId());
        assertEquals("user@example.com", user.email());
    }

    @Test
    void rejectsExpiredSupabaseJwt() {
        var resolver = new SupabaseJwtUserPrincipalResolver(
                SIGNING_KEY,
                "http://localhost:8000/auth/v1",
                "authenticated",
                Clock.fixed(NOW, ZoneOffset.UTC));
        String token = token("""
                {
                  "iss": "http://localhost:8000/auth/v1",
                  "aud": "authenticated",
                  "exp": 1700000000,
                  "sub": "%s",
                  "email": "user@example.com"
                }
                """.formatted(USER_ID));

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> resolver.resolve(new UserAuthContext("Bearer " + token, null)));

        assertEquals("unauthorized", exception.code());
    }

    private static String token(String payloadJson) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode(payloadJson);
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
