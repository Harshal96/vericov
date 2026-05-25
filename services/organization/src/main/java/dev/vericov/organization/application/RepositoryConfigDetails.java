package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepositoryConfigDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String source,
        Map<String, Object> config,
        int schemaVersion,
        String validationStatus,
        List<String> validationErrors,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryConfigDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(source, "source");
        config = ConfigurationValues.deepCopyMap(config);
        Objects.requireNonNull(validationStatus, "validationStatus");
        validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
        Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryConfigDetails withValues(
            Map<String, Object> nextConfig,
            int nextSchemaVersion,
            String nextValidationStatus,
            List<String> nextValidationErrors,
            UUID nextUpdatedByUserId,
            Instant nextUpdatedAt) {
        return new RepositoryConfigDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                source,
                nextConfig,
                nextSchemaVersion,
                nextValidationStatus,
                nextValidationErrors,
                nextUpdatedByUserId,
                createdAt,
                nextUpdatedAt);
    }
}
