package dev.vericov.integrations.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;

public record UpdateIntegrationSyncStateHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("sync_type")
        String syncType,
        @JsonbProperty("scope_type")
        String scopeType,
        @JsonbProperty("scope_id")
        String scopeId,
        String status,
        Map<String, Object> cursor,
        Map<String, Object> checkpoint,
        @JsonbProperty("last_error")
        Map<String, Object> lastError,
        @JsonbProperty("last_started_at")
        String lastStartedAt,
        @JsonbProperty("last_completed_at")
        String lastCompletedAt,
        @JsonbProperty("next_run_at")
        String nextRunAt,
        @JsonbProperty("lease_expires_at")
        String leaseExpiresAt) {
}
