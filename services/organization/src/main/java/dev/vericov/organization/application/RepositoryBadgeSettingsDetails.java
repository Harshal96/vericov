package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepositoryBadgeSettingsDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        boolean enabled,
        String branch,
        String metric,
        String label,
        Map<String, Object> thresholds,
        String tokenHash,
        String tokenPrefix,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt) {

    public RepositoryBadgeSettingsDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(label, "label");
        thresholds = ConfigurationValues.deepCopyMap(thresholds);
        Objects.requireNonNull(createdByUserId, "createdByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryBadgeSettingsDetails withValues(
            boolean nextEnabled,
            String nextBranch,
            String nextMetric,
            String nextLabel,
            Map<String, Object> nextThresholds,
            Instant nextUpdatedAt) {
        return new RepositoryBadgeSettingsDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                nextEnabled,
                nextBranch,
                nextMetric,
                nextLabel,
                nextThresholds,
                tokenHash,
                tokenPrefix,
                createdByUserId,
                createdAt,
                nextUpdatedAt,
                revokedAt);
    }

    public RepositoryBadgeSettingsDetails withToken(
            String nextTokenHash,
            String nextTokenPrefix,
            Instant nextUpdatedAt) {
        return new RepositoryBadgeSettingsDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                enabled,
                branch,
                metric,
                label,
                thresholds,
                nextTokenHash,
                nextTokenPrefix,
                createdByUserId,
                createdAt,
                nextUpdatedAt,
                null);
    }
}
