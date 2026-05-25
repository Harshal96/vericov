package dev.vericov.git.application;

import dev.vericov.git.adapter.provider.github.GitHubWebhookVerifier;
import dev.vericov.git.application.port.GitEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitWebhookServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void verifiesStoresDeduplicatesAndPublishesNormalizedWebhook() throws Exception {
        char[] signingKey = "webhook-signing-fixture".toCharArray();
        InMemoryGitActionRepository repository = new InMemoryGitActionRepository();
        RecordingPublisher publisher = new RecordingPublisher();
        GitWebhookService service = new GitWebhookService(
                repository,
                new GitHubWebhookVerifier(signingKey),
                publisher);
        byte[] payload = """
                {"action":"opened","repository":{"id":123,"full_name":"acme/widget"},"pull_request":{"id":456,"number":42,"state":"open","html_url":"https://github.com/acme/widget/pull/42","head":{"ref":"feature","sha":"head123"},"base":{"ref":"main","sha":"base123"},"user":{"login":"octocat"},"title":"Add feature"}}
                """.getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + hmacSha256(signingKey, payload);

        GitWebhookProcessingResult first = service.receive(new GitWebhookCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                null,
                "github",
                "pull_request",
                "delivery-1",
                signature,
                payload,
                Instant.parse("2026-05-23T19:00:00Z")));
        GitWebhookProcessingResult second = service.receive(new GitWebhookCommand(
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                null,
                "github",
                "pull_request",
                "delivery-1",
                signature,
                payload,
                Instant.parse("2026-05-23T19:00:01Z")));

        assertEquals("processed", first.status());
        assertEquals("duplicate", second.status());
        assertEquals(1, publisher.events.size());
        assertEquals("git.webhook.pull_request", publisher.events.getFirst().eventType());
        assertEquals("acme/widget", publisher.events.getFirst().normalizedPayload().get("repository_full_name"));
        assertEquals(42, publisher.events.getFirst().normalizedPayload().get("pull_request_number"));
        assertTrue(repository.findWebhookEvent("github", "delivery-1").orElseThrow().signatureValid());
    }

    private static String hmacSha256(char[] signingKey, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new String(signingKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload));
    }

    private static final class RecordingPublisher implements GitEventPublisher {
        private final java.util.ArrayList<PublishedGitEvent> events = new java.util.ArrayList<>();

        @Override
        public void publish(PublishedGitEvent event) {
            events.add(event);
        }
    }
}
