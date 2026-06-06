package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryPackageNodeDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID componentId,
        String packageName,
        String packagePath,
        String manifestPath,
        String ecosystem,
        Map<String, Object> metadata,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryPackageNodeDetails {
        metadata = ConfigurationValues.deepCopyMap(metadata);
    }
}
