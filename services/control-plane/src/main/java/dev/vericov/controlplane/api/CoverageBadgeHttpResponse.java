package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageBadgeDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CoverageBadgeHttpResponse(
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String label,
        String message,
        String color,
        String metric,
        String branch,
        @JsonbProperty("commit_sha")
        String commitSha,
        @JsonbProperty("coverage_percent")
        BigDecimal coveragePercent,
        @JsonbProperty("report_created_at")
        Instant reportCreatedAt,
        @JsonbProperty("resolved_at")
        Instant resolvedAt) {

    public static CoverageBadgeHttpResponse from(CoverageBadgeDetails details) {
        return new CoverageBadgeHttpResponse(
                details.organizationId(),
                details.repositoryId(),
                details.label(),
                details.message(),
                details.color(),
                details.metric(),
                details.branch(),
                details.commitSha(),
                details.coveragePercent(),
                details.reportCreatedAt(),
                details.resolvedAt());
    }
}
