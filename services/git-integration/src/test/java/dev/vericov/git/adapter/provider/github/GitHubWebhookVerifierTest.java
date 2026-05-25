package dev.vericov.git.adapter.provider.github;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubWebhookVerifierTest {

    @Test
    void verifiesSha256SignatureUsingConstantTimeComparison() throws Exception {
        byte[] payload = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        char[] signingKey = "webhook-signing-fixture".toCharArray();
        String signature = "sha256=" + hmacSha256(signingKey, payload);

        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(signingKey);

        assertTrue(verifier.verify("github", "pull_request", "delivery-1", signature, payload));
        assertFalse(verifier.verify("github", "pull_request", "delivery-1", "sha256=bad", payload));
        assertFalse(verifier.verify("gitlab", "pull_request", "delivery-1", signature, payload));
    }

    private static String hmacSha256(char[] signingKey, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new String(signingKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
