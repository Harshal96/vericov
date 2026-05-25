package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationSyncStateDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationSyncStateHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("connection_id")
        UUID connectionId,
        @JsonbProperty("sync_type")
        String syncType,
        @JsonbProperty("scope_type")
        String scopeType,
        @JsonbProperty("scope_id")
        UUID scopeId,
        String status,
        Map<String, Object> cursor,
        Map<String, Object> checkpoint,
        @JsonbProperty("last_error")
        Map<String, Object> lastError,
        @JsonbProperty("last_started_at")
        Instant lastStartedAt,
        @JsonbProperty("last_completed_at")
        Instant lastCompletedAt,
        @JsonbProperty("next_run_at")
        Instant nextRunAt,
        @JsonbProperty("lease_expires_at")
        Instant leaseExpiresAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationSyncStateHttpResponse from(IntegrationSyncStateDetails syncState) {
        return new IntegrationSyncStateHttpResponse(
                syncState.id(),
                syncState.tenantId(),
                syncState.orgId(),
                syncState.connectionId(),
                syncState.syncType(),
                syncState.scopeType(),
                syncState.scopeId(),
                syncState.status(),
                syncState.cursor(),
                syncState.checkpoint(),
                syncState.lastError(),
                syncState.lastStartedAt(),
                syncState.lastCompletedAt(),
                syncState.nextRunAt(),
                syncState.leaseExpiresAt(),
                syncState.createdAt(),
                syncState.updatedAt());
    }
}
