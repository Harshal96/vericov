package dev.vericov.organization.adapter.auth;

import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SupabaseJwtUserPrincipalResolver implements UserPrincipalResolver {
    private final byte[] jwtSecret;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public SupabaseJwtUserPrincipalResolver(String jwtSecret, String issuer, String audience, Clock clock) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("jwtSecret is required");
        }
        this.jwtSecret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.issuer = blankToNull(issuer);
        this.audience = blankToNull(audience);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthenticatedUser resolve(UserAuthContext context) {
        String token = bearerToken(context.authorizationHeader());
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw unauthorized();
        }

        JsonObject header = parseJson(parts[0]);
        JsonObject payload = parseJson(parts[1]);
        if (!"HS256".equals(stringClaim(header, "alg"))) {
            throw unauthorized();
        }
        verifySignature(parts[0] + "." + parts[1], parts[2]);
        validateRegisteredClaims(payload);

        String subject = stringClaim(payload, "sub");
        if (subject == null) {
            throw unauthorized();
        }
        try {
            return new AuthenticatedUser(UUID.fromString(subject), stringClaim(payload, "email"));
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private void validateRegisteredClaims(JsonObject payload) {
        if (issuer != null && !issuer.equals(stringClaim(payload, "iss"))) {
            throw unauthorized();
        }
        if (audience != null && !audienceMatches(payload.get("aud"))) {
            throw unauthorized();
        }
        long now = clock.instant().getEpochSecond();
        Long expiresAt = longClaim(payload, "exp");
        if (expiresAt == null || expiresAt <= now) {
            throw unauthorized();
        }
        Long notBefore = longClaim(payload, "nbf");
        if (notBefore != null && notBefore > now) {
            throw unauthorized();
        }
    }

    private boolean audienceMatches(JsonValue value) {
        if (value instanceof JsonString jsonString) {
            return audience.equals(jsonString.getString());
        }
        if (value instanceof JsonArray jsonArray) {
            return jsonArray.stream()
                    .filter(JsonString.class::isInstance)
                    .map(JsonString.class::cast)
                    .anyMatch(jsonString -> audience.equals(jsonString.getString()));
        }
        return false;
    }

    private void verifySignature(String signingInput, String encodedSignature) {
        byte[] actual = decode(encodedSignature);
        byte[] expected = hmacSha256(signingInput);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthorized();
        }
    }

    private byte[] hmacSha256(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw unauthorized();
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw unauthorized();
        }
        String token = trimmed.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthorized();
        }
        return token;
    }

    private static JsonObject parseJson(String encoded) {
        try (var reader = Json.createReader(new StringReader(new String(decode(encoded), StandardCharsets.UTF_8)))) {
            return reader.readObject();
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private static byte[] decode(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private static String stringClaim(JsonObject object, String name) {
        JsonValue value = object.get(name);
        return value instanceof JsonString jsonString ? jsonString.getString() : null;
    }

    private static Long longClaim(JsonObject object, String name) {
        if (!object.containsKey(name)) {
            return null;
        }
        try {
            return object.getJsonNumber(name).longValue();
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static OrganizationException unauthorized() {
        return new OrganizationException("unauthorized", "Supabase authentication is required");
    }
}
