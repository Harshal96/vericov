package dev.vericov.git.api;

import dev.vericov.git.adapter.provider.github.GitHubWebhookVerifier;
import dev.vericov.git.application.GitWebhookService;
import dev.vericov.git.application.InMemoryGitActionRepository;
import dev.vericov.git.application.port.GitEventPublisher;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitWebhookResourceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void acceptsSignedGithubWebhook() throws Exception {
        char[] signingKey = "webhook-signing-fixture".toCharArray();
        GitWebhookResource resource = new GitWebhookResource(new GitWebhookService(
                new InMemoryGitActionRepository(),
                new GitHubWebhookVerifier(signingKey),
                event -> { }));
        String payload = "{\"action\":\"opened\",\"repository\":{\"full_name\":\"acme/widget\"}}";

        Response response = resource.receive(
                "github",
                TENANT_ID.toString(),
                ORG_ID.toString(),
                REPOSITORY_ID.toString(),
                CONNECTION_ID.toString(),
                null,
                "delivery-1",
                "pull_request",
                "sha256=" + hmacSha256(signingKey, payload.getBytes(StandardCharsets.UTF_8)),
                payload);

        assertEquals(202, response.getStatus());
    }

    @Test
    void rejectsInvalidSignature() {
        GitWebhookResource resource = new GitWebhookResource(new GitWebhookService(
                new InMemoryGitActionRepository(),
                new GitHubWebhookVerifier("webhook-signing-fixture".toCharArray()),
                event -> { }));

        Response response = resource.receive(
                "github",
                TENANT_ID.toString(),
                ORG_ID.toString(),
                REPOSITORY_ID.toString(),
                CONNECTION_ID.toString(),
                null,
                "delivery-1",
                "pull_request",
                "sha256=bad",
                "{\"action\":\"opened\"}");

        assertEquals(401, response.getStatus());
    }

    private static String hmacSha256(char[] signingKey, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new String(signingKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
