package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationWebhookEndpointDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record IntegrationWebhookEndpointHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("connection_id")
        UUID connectionId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("external_webhook_id")
        String externalWebhookId,
        @JsonbProperty("endpoint_url")
        String endpointUrl,
        @JsonbProperty("event_types")
        List<String> eventTypes,
        String status,
        @JsonbProperty("signing_secret_ref")
        String signingSecretRef,
        Map<String, Object> config,
        @JsonbProperty("last_delivery")
        Map<String, Object> lastDelivery,
        @JsonbProperty("last_delivered_at")
        Instant lastDeliveredAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationWebhookEndpointHttpResponse from(IntegrationWebhookEndpointDetails endpoint) {
        return new IntegrationWebhookEndpointHttpResponse(
                endpoint.id(),
                endpoint.tenantId(),
                endpoint.orgId(),
                endpoint.connectionId(),
                endpoint.providerKey(),
                endpoint.externalWebhookId(),
                endpoint.endpointUrl(),
                endpoint.eventTypes(),
                endpoint.status(),
                endpoint.signingSecretRef(),
                endpoint.config(),
                endpoint.lastDelivery(),
                endpoint.lastDeliveredAt(),
                endpoint.createdAt(),
                endpoint.updatedAt());
    }
}
