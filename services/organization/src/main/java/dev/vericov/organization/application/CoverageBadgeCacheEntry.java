package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CoverageBadgeCacheEntry(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String cacheScope,
        String branch,
        String metric,
        String label,
        String message,
        String color,
        String commitSha,
        BigDecimal coveragePercent,
        UUID sourceReportId,
        Instant reportCreatedAt,
        Instant settingsUpdatedAt,
        Instant cachedAt,
        Instant expiresAt) {

    public CoverageBadgeCacheEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(cacheScope, "cacheScope");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(settingsUpdatedAt, "settingsUpdatedAt");
        Objects.requireNonNull(cachedAt, "cachedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public CoverageBadgeDetails toDetails() {
        return new CoverageBadgeDetails(
                organizationId,
                repositoryId,
                label,
                message,
                color,
                metric,
                branch,
                commitSha,
                coveragePercent,
                reportCreatedAt,
                cachedAt);
    }
}
