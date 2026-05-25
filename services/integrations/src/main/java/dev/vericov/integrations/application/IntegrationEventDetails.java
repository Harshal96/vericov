package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IntegrationEventDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        UUID webhookEndpointId,
        String providerKey,
        String eventType,
        String externalEventId,
        String scopeType,
        UUID scopeId,
        String status,
        Map<String, Object> payload,
        Map<String, Object> error,
        Instant receivedAt,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationEventDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        providerKey = IntegrationConfigValues.requireCanonical(providerKey, "providerKey");
        eventType = IntegrationConfigValues.requireTrimmed(eventType, "eventType");
        if (externalEventId != null) {
            externalEventId = externalEventId.trim();
        }
        if (scopeType != null) {
            scopeType = IntegrationConfigValues.requireCanonical(scopeType, "scopeType");
            Objects.requireNonNull(scopeId, "scopeId");
        }
        status = IntegrationConfigValues.requireCanonical(status, "status");
        payload = IntegrationConfigValues.deepCopyMap(payload);
        error = IntegrationConfigValues.deepCopyMap(error);
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
