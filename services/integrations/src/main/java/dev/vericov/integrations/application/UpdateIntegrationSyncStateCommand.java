package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdateIntegrationSyncStateCommand(
        String requestedBy,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String syncType,
        String scopeType,
        UUID scopeId,
        String status,
        Map<String, Object> cursor,
        Map<String, Object> checkpoint,
        Map<String, Object> lastError,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        Instant nextRunAt,
        Instant leaseExpiresAt) {
}
