package dev.vericov.integrations.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record DisableIntegrationConnectionHttpRequest(
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID orgId,
        @JsonbProperty("expected_updated_at")
        Instant expectedUpdatedAt) {
}
