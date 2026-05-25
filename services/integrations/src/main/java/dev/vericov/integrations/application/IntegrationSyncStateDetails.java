package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IntegrationSyncStateDetails(
        UUID id,
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
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationSyncStateDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(connectionId, "connectionId");
        syncType = IntegrationConfigValues.requireCanonical(syncType, "syncType");
        scopeType = IntegrationConfigValues.requireCanonical(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
        status = IntegrationConfigValues.requireCanonical(status, "status");
        cursor = IntegrationConfigValues.deepCopyMap(cursor);
        checkpoint = IntegrationConfigValues.deepCopyMap(checkpoint);
        lastError = IntegrationConfigValues.deepCopyMap(lastError);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
