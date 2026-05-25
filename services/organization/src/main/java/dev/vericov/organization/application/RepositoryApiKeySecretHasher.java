package dev.vericov.organization.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RepositoryApiKeySecretHasher {
    private final byte[] pepper;

    RepositoryApiKeySecretHasher(String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("pepper is required");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    String hash(String plaintextKey) {
        if (plaintextKey == null || plaintextKey.isBlank()) {
            throw new IllegalArgumentException("plaintextKey is required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plaintextKey.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    boolean matches(String plaintextKey, String expectedHash) {
        if (plaintextKey == null || expectedHash == null) {
            return false;
        }
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(plaintextKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
