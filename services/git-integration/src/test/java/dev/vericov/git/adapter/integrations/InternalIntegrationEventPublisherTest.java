package dev.vericov.git.adapter.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.git.application.PublishedGitEvent;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalIntegrationEventPublisherTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant RECEIVED_AT = Instant.parse("2026-05-22T10:15:30Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-05-22T10:15:31Z");

    @Test
    void publishesRepositoryEventsWithServiceHeadersAndNormalizedPayload() {
        RecordingTransport transport = new RecordingTransport(201);
        InternalIntegrationEventPublisher publisher = publisher(transport);

        publisher.publish(event(REPOSITORY_ID));

        assertEquals(1, transport.calls);
        assertEquals(
                "git-integration",
                transport.lastRequest.headers().firstValue("X-Vericov-Service-Name").orElseThrow());
        assertEquals(
                "service-token",
                transport.lastRequest.headers().firstValue("X-Vericov-Service-Token").orElseThrow());
        assertEquals(
                URI.create("http://integrations:8084/internal/v1/integrations/events"),
                transport.lastRequest.uri());
        JsonObject body = jsonObject(transport.lastBody);
        assertEquals(TENANT_ID.toString(), body.getString("tenant_id"));
        assertEquals(REPOSITORY_ID.toString(), body.getString("scope_id"));
        assertEquals("repository", body.getString("scope_type"));
        assertEquals("github", body.getString("provider_key"));
        assertEquals("pull_request", body.getString("event_type"));
        assertEquals("processed", body.getString("status"));
        assertEquals("opened", body.getJsonObject("payload").getString("action"));
    }

    @Test
    void publishesOrganizationScopedEventsWhenRepositoryIsAbsent() {
        RecordingTransport transport = new RecordingTransport(200);
        InternalIntegrationEventPublisher publisher = publisher(transport);

        publisher.publish(event(null));

        JsonObject body = jsonObject(transport.lastBody);
        assertEquals("organization", body.getString("scope_type"));
        assertEquals(ORG_ID.toString(), body.getString("scope_id"));
    }

    @Test
    void skipsEventsMissingRoutingContext() {
        RecordingTransport transport = new RecordingTransport(200);
        InternalIntegrationEventPublisher publisher = publisher(transport);

        publisher.publish(new PublishedGitEvent(
                null,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                "github",
                "pull_request",
                "delivery-1",
                Map.of("action", "opened"),
                RECEIVED_AT,
                PROCESSED_AT));

        assertEquals(0, transport.calls);
    }

    @Test
    void rejectsFailedPublishResponses() {
        RecordingTransport transport = new RecordingTransport(503);
        InternalIntegrationEventPublisher publisher = publisher(transport);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> publisher.publish(event(REPOSITORY_ID)));

        assertTrue(exception.getMessage().contains("HTTP 503"));
    }

    @Test
    void rejectsBlankServiceIdentityAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InternalIntegrationEventPublisher(
                        URI.create("http://integrations:8084"),
                        " ",
                        "service-token",
                        new RecordingTransport(200)));
    }

    private static InternalIntegrationEventPublisher publisher(RecordingTransport transport) {
        return new InternalIntegrationEventPublisher(
                URI.create("http://integrations:8084"),
                "git-integration",
                "service-token",
                transport);
    }

    private static PublishedGitEvent event(UUID repositoryId) {
        return new PublishedGitEvent(
                TENANT_ID,
                ORG_ID,
                repositoryId,
                CONNECTION_ID,
                "github",
                "pull_request",
                "delivery-1",
                Map.of("action", "opened"),
                RECEIVED_AT,
                PROCESSED_AT);
    }

    private static JsonObject jsonObject(String body) {
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }

    private static final class RecordingTransport implements InternalIntegrationEventPublisher.HttpTransport {
        private final int statusCode;
        private int calls;
        private HttpRequest lastRequest;
        private String lastBody;

        private RecordingTransport(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public InternalIntegrationEventPublisher.HttpResult send(HttpRequest request, String body) throws IOException {
            calls++;
            lastRequest = request;
            lastBody = body;
            return new InternalIntegrationEventPublisher.HttpResult(statusCode, "{}");
        }
    }
}
