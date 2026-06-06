package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryConfigDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryConfigHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String source,
        Map<String, Object> config,
        @JsonbProperty("schema_version")
        int schemaVersion,
        @JsonbProperty("validation_status")
        String validationStatus,
        @JsonbProperty("validation_errors")
        List<String> validationErrors,
        @JsonbProperty("updated_by_user_id")
        UUID updatedByUserId,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryConfigHttpResponse from(RepositoryConfigDetails details) {
        return new RepositoryConfigHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.source(),
                details.config(),
                details.schemaVersion(),
                details.validationStatus(),
                details.validationErrors(),
                details.updatedByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
