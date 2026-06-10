package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record CoverageReportDetails(
        UUID uploadId,
        UUID repositoryId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String status,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement,
        String normalizedStorageBucket,
        String normalizedStoragePath,
        Instant createdAt) {
}
