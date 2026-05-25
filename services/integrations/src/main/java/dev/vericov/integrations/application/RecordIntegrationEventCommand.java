package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RecordIntegrationEventCommand(
        String requestedBy,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String providerKey,
        String eventType,
        String externalEventId,
        String scopeType,
        UUID scopeId,
        String status,
        Map<String, Object> payload,
        Map<String, Object> error,
        Instant receivedAt,
        Instant processedAt) {
}
