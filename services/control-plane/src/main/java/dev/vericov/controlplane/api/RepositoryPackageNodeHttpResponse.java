package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryPackageNodeDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryPackageNodeHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("component_id")
        UUID componentId,
        @JsonbProperty("package_name")
        String packageName,
        @JsonbProperty("package_path")
        String packagePath,
        @JsonbProperty("manifest_path")
        String manifestPath,
        String ecosystem,
        @JsonbProperty("metadata")
        Map<String, Object> metadata,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryPackageNodeHttpResponse from(RepositoryPackageNodeDetails details) {
        return new RepositoryPackageNodeHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.componentId(),
                details.packageName(),
                details.packagePath(),
                details.manifestPath(),
                details.ecosystem(),
                details.metadata(),
                details.status(),
                details.createdAt(),
                details.updatedAt());
    }
}
