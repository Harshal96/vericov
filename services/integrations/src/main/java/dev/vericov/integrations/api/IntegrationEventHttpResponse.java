package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationEventDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationEventHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("connection_id")
        UUID connectionId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("event_type")
        String eventType,
        @JsonbProperty("external_event_id")
        String externalEventId,
        @JsonbProperty("scope_type")
        String scopeType,
        @JsonbProperty("scope_id")
        UUID scopeId,
        String status,
        Map<String, Object> payload,
        Map<String, Object> error,
        @JsonbProperty("received_at")
        Instant receivedAt,
        @JsonbProperty("processed_at")
        Instant processedAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationEventHttpResponse from(IntegrationEventDetails event) {
        return new IntegrationEventHttpResponse(
                event.id(),
                event.tenantId(),
                event.orgId(),
                event.connectionId(),
                event.providerKey(),
                event.eventType(),
                event.externalEventId(),
                event.scopeType(),
                event.scopeId(),
                event.status(),
                event.payload(),
                event.error(),
                event.receivedAt(),
                event.processedAt(),
                event.createdAt(),
                event.updatedAt());
    }
}
