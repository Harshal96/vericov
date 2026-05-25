package dev.vericov.integrations.api;

import dev.vericov.integrations.application.UpsertIntegrationBindingCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpsertIntegrationBindingHttpRequest(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        List<String> capabilities,
        Map<String, Object> config,
        String status,
        @JsonbProperty("expected_updated_at")
        Instant expectedUpdatedAt) {

    public UpsertIntegrationBindingCommand toCommand(
            UUID requesterUserId,
            UUID connectionId,
            String scopeType,
            UUID scopeId) {
        return new UpsertIntegrationBindingCommand(
                requesterUserId,
                tenantId,
                orgId,
                connectionId,
                scopeType,
                scopeId,
                capabilities,
                config,
                status,
                expectedUpdatedAt);
    }
}
