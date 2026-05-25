package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CoverageReportDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID uploadId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        CoverageMetricDetails line,
        CoverageMetricDetails branchMetric,
        CoverageMetricDetails function,
        CoverageMetricDetails statement,
        List<CoverageFileSummaryDetails> files,
        Instant createdAt,
        Instant updatedAt) {

    public CoverageReportDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(branchMetric, "branchMetric");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(statement, "statement");
        files = List.copyOf(files == null ? List.of() : files);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CoverageReportDetails from(
            UUID organizationId,
            CoverageReportSummary summary,
            List<CoverageFileSummaryDetails> files) {
        return new CoverageReportDetails(
                summary.id(),
                summary.tenantId(),
                organizationId,
                summary.repositoryId(),
                summary.uploadId(),
                summary.commitSha(),
                summary.branch(),
                summary.pullRequestNumber(),
                CoverageMetricDetails.of(summary.lineCovered(), summary.lineTotal()),
                CoverageMetricDetails.of(summary.branchCovered(), summary.branchTotal()),
                CoverageMetricDetails.of(summary.functionCovered(), summary.functionTotal()),
                CoverageMetricDetails.of(summary.statementCovered(), summary.statementTotal()),
                files,
                summary.createdAt(),
                summary.updatedAt());
    }
}
