package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.UUID;

public record RepositoryDashboardDetails(
        UUID organizationId,
        UUID repositoryId,
        String branch,
        String latestCommitSha,
        CoverageMetricDetails latestLineCoverage,
        CoverageMetricDetails latestBranchCoverage,
        CoverageMetricDetails latestFunctionCoverage,
        CoverageMetricDetails latestStatementCoverage,
        int failingGateCount,
        Instant latestReportAt) {
}
