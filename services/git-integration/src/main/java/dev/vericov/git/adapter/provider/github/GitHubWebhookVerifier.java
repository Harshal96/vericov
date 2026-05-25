package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.port.GitWebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class GitHubWebhookVerifier implements GitWebhookVerifier {
    private static final String PROVIDER_KEY = "github";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final byte[] secret;

    public GitHubWebhookVerifier(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("GitHub webhook secret is required");
        }
        this.secret = new String(secret).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean verify(String providerKey, String eventType, String deliveryId, String signature, byte[] payload) {
        if (!PROVIDER_KEY.equals(normalize(providerKey))
                || isBlank(eventType)
                || isBlank(deliveryId)
                || signature == null
                || !signature.startsWith(SIGNATURE_PREFIX)
                || payload == null) {
            return false;
        }
        byte[] expected = hmacSha256(payload);
        byte[] provided = parseHex(signature.substring(SIGNATURE_PREFIX.length()));
        return provided.length == expected.length && MessageDigest.isEqual(expected, provided);
    }

    private byte[] hmacSha256(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        } catch (java.security.InvalidKeyException exception) {
            throw new IllegalStateException("GitHub webhook secret is invalid", exception);
        }
    }

    private static byte[] parseHex(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            return new byte[0];
        }
        return HexFormat.of().parseHex(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    @Override
    public String toString() {
        return "GitHubWebhookVerifier[secret=<redacted>, length=" + secret.length + "]";
    }
}
