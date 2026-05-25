package dev.vericov.git.adapter.integrations;

import dev.vericov.git.application.PublishedGitEvent;
import dev.vericov.git.application.port.GitEventPublisher;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class InternalIntegrationEventPublisher implements GitEventPublisher {
    private final URI baseUri;
    private final String serviceName;
    private final String serviceToken;
    private final HttpTransport transport;
    private final Jsonb jsonb = JsonbBuilder.create();

    public InternalIntegrationEventPublisher(URI baseUri, String serviceName, String serviceToken) {
        this(baseUri, serviceName, serviceToken, new JavaHttpTransport(HttpClient.newHttpClient()));
    }

    InternalIntegrationEventPublisher(
            URI baseUri,
            String serviceName,
            String serviceToken,
            HttpTransport transport) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.serviceName = requireText(serviceName, "serviceName");
        this.serviceToken = requireText(serviceToken, "serviceToken");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public void publish(PublishedGitEvent event) {
        if (event.tenantId() == null || event.orgId() == null || event.connectionId() == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", event.tenantId().toString());
        body.put("org_id", event.orgId().toString());
        body.put("connection_id", event.connectionId().toString());
        body.put("provider_key", event.providerKey());
        body.put("event_type", event.eventType());
        body.put("external_event_id", event.deliveryId());
        body.put("scope_type", event.repositoryId() == null ? "organization" : "repository");
        body.put("scope_id", event.repositoryId() == null ? event.orgId().toString() : event.repositoryId().toString());
        body.put("status", "processed");
        body.put("payload", event.normalizedPayload());
        body.put("error", Map.of());
        body.put("received_at", event.receivedAt().toString());
        body.put("processed_at", event.processedAt().toString());
        String json = jsonb.toJson(body);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/internal/v1/integrations/events"))
                .header("Content-Type", "application/json")
                .header("X-Vericov-Service-Name", serviceName)
                .header("X-Vericov-Service-Token", serviceToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResult result = transport.send(request, json);
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                throw new IllegalStateException("Integrations event publish failed with HTTP " + result.statusCode());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Integrations event publish failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Integrations event publish interrupted", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    public interface HttpTransport {
        HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException;
    }

    public record HttpResult(int statusCode, String body) {
    }

    private record JavaHttpTransport(HttpClient httpClient) implements HttpTransport {
        @Override
        public HttpResult send(HttpRequest request, String body) throws IOException, InterruptedException {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        }
    }
}
