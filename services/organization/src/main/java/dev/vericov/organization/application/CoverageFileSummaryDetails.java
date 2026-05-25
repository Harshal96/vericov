package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CoverageFileSummaryDetails(
        UUID id,
        UUID tenantId,
        UUID coverageReportId,
        UUID repositoryId,
        String commitSha,
        String filePath,
        int lineCovered,
        int lineTotal,
        int branchCovered,
        int branchTotal,
        int functionCovered,
        int functionTotal,
        int statementCovered,
        int statementTotal,
        Instant createdAt) {

    public CoverageFileSummaryDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(coverageReportId, "coverageReportId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public CoverageMetricDetails line() {
        return CoverageMetricDetails.of(lineCovered, lineTotal);
    }

    public CoverageMetricDetails branch() {
        return CoverageMetricDetails.of(branchCovered, branchTotal);
    }

    public CoverageMetricDetails function() {
        return CoverageMetricDetails.of(functionCovered, functionTotal);
    }

    public CoverageMetricDetails statement() {
        return CoverageMetricDetails.of(statementCovered, statementTotal);
    }
}
