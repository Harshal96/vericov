package dev.vericov.integrations.api;

import dev.vericov.integrations.application.UpdateIntegrationConnectionCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdateIntegrationConnectionHttpRequest(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("display_name")
        String displayName,
        String status,
        Map<String, Object> config,
        @JsonbProperty("expected_updated_at")
        Instant expectedUpdatedAt) {

    public UpdateIntegrationConnectionCommand toCommand(UUID requesterUserId, UUID connectionId) {
        return new UpdateIntegrationConnectionCommand(
                requesterUserId,
                tenantId,
                orgId,
                connectionId,
                displayName,
                status,
                config,
                expectedUpdatedAt);
    }
}
