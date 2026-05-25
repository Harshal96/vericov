package dev.vericov.analysis.coverage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CoverageReportSummary(
        UUID reportId,
        UUID tenantId,
        UUID repositoryId,
        UUID uploadId,
        String commitSha,
        String branchName,
        Integer pullRequestNumber,
        Instant createdAt,
        Instant updatedAt) {

    public CoverageReportSummary {
        Objects.requireNonNull(reportId, "reportId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branchName, "branchName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
