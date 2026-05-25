package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class UploadJwtSupport {
    private UploadJwtSupport() {
    }

    static JsonObject verifiedHs256Payload(
            String token,
            String secret,
            String issuer,
            String audience,
            Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw unauthorized();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw unauthorized();
        }
        JsonObject header = parseJson(parts[0]);
        JsonObject payload = parseJson(parts[1]);
        if (!"HS256".equals(stringClaim(header, "alg"))) {
            throw unauthorized();
        }
        byte[] actual = decode(parts[2]);
        byte[] expected = hmacSha256(secret, parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthorized();
        }
        validateRegisteredClaims(payload, issuer, audience, clock);
        return payload;
    }

    static JsonObject parseUnverifiedPayload(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw unauthorized();
        }
        return parseJson(parts[1]);
    }

    static String stringClaim(JsonObject object, String name) {
        JsonValue value = object.get(name);
        return value instanceof JsonString jsonString ? jsonString.getString() : null;
    }

    static UUID uuidClaim(JsonObject object, String name) {
        String value = stringClaim(object, name);
        if (value == null) {
            throw unauthorized();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    static List<String> stringArrayClaim(JsonObject object, String name, List<String> fallback) {
        JsonValue value = object.get(name);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof JsonArray array)) {
            throw unauthorized();
        }
        List<String> strings = new ArrayList<>();
        for (JsonValue item : array) {
            if (!(item instanceof JsonString string)) {
                throw unauthorized();
            }
            strings.add(string.getString());
        }
        return List.copyOf(strings);
    }

    static void validateRegisteredClaims(JsonObject payload, String issuer, String audience, Clock clock) {
        if (issuer != null && !issuer.isBlank() && !issuer.equals(stringClaim(payload, "iss"))) {
            throw unauthorized();
        }
        if (audience != null && !audience.isBlank() && !audienceMatches(payload.get("aud"), audience)) {
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

    private static boolean audienceMatches(JsonValue value, String audience) {
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

    private static byte[] hmacSha256(String secret, String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    static InvalidUploadException unauthorized() {
        return new InvalidUploadException("unauthorized", "Invalid upload credential");
    }
}
