package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RepositoryDashboardSummaryDetails(
        UUID repositoryId,
        String fullName,
        String branch,
        String latestCommitSha,
        BigDecimal latestLineCoverage,
        int failingGateCount,
        Instant latestReportAt) {
}
