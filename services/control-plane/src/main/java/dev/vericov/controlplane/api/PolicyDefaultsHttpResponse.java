package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.PolicyDefaultsDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PolicyDefaultsHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        Map<String, Object> defaults,
        @JsonbProperty("schema_version")
        int schemaVersion,
        @JsonbProperty("updated_by_user_id")
        UUID updatedByUserId,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static PolicyDefaultsHttpResponse from(PolicyDefaultsDetails details) {
        return new PolicyDefaultsHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.defaults(),
                details.schemaVersion(),
                details.updatedByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
