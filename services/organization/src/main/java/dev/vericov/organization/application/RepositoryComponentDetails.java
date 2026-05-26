package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryComponentDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        Map<String, Object> metadata,
        String status,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryComponentDetails {
        pathPatterns = List.copyOf(pathPatterns == null ? List.of() : pathPatterns);
        owners = List.copyOf(owners == null ? List.of() : owners);
        metadata = ConfigurationValues.deepCopyMap(metadata);
    }

    public RepositoryComponentDetails withValues(
            String name,
            String description,
            List<String> pathPatterns,
            List<String> owners,
            String criticality,
            Map<String, Object> metadata,
            String status,
            Instant updatedAt) {
        return new RepositoryComponentDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                name,
                description,
                pathPatterns,
                owners,
                criticality,
                metadata,
                status,
                createdByUserId,
                createdAt,
                updatedAt);
    }
}
