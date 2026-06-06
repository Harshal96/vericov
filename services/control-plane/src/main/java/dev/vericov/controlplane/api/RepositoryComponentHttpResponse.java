package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryComponentDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryComponentHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String name,
        String description,
        @JsonbProperty("path_patterns")
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        @JsonbProperty("metadata")
        Map<String, Object> metadata,
        String status,
        @JsonbProperty("created_by_user_id")
        UUID createdByUserId,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryComponentHttpResponse from(RepositoryComponentDetails details) {
        return new RepositoryComponentHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.name(),
                details.description(),
                details.pathPatterns(),
                details.owners(),
                details.criticality(),
                details.metadata(),
                details.status(),
                details.createdByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
