package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CoverageBadgeDetails(
        UUID organizationId,
        UUID repositoryId,
        String label,
        String message,
        String color,
        String metric,
        String branch,
        String commitSha,
        BigDecimal coveragePercent,
        Instant reportCreatedAt,
        Instant resolvedAt) {

    public CoverageBadgeDetails {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
    }
}
