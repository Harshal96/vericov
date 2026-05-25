package dev.vericov.integrations.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;

public record RecordIntegrationEventHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("connection_id")
        String connectionId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("event_type")
        String eventType,
        @JsonbProperty("external_event_id")
        String externalEventId,
        @JsonbProperty("scope_type")
        String scopeType,
        @JsonbProperty("scope_id")
        String scopeId,
        String status,
        Map<String, Object> payload,
        Map<String, Object> error,
        @JsonbProperty("received_at")
        String receivedAt,
        @JsonbProperty("processed_at")
        String processedAt) {
}
