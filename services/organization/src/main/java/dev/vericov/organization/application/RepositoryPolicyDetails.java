package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepositoryPolicyDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        int priority,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryPolicyDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(policyType, "policyType");
        Objects.requireNonNull(targetType, "targetType");
        config = ConfigurationValues.deepCopyMap(config);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdByUserId, "createdByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryPolicyDetails withValues(
            String nextName,
            String nextDescription,
            String nextPolicyType,
            String nextTargetType,
            String nextTargetSelector,
            Map<String, Object> nextConfig,
            String nextStatus,
            int nextPriority,
            Instant nextUpdatedAt) {
        return new RepositoryPolicyDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                nextName,
                nextDescription,
                nextPolicyType,
                nextTargetType,
                nextTargetSelector,
                nextConfig,
                nextStatus,
                nextPriority,
                createdByUserId,
                createdAt,
                nextUpdatedAt);
    }
}
