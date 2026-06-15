package dev.vericov.upload.application;

import java.time.Instant;
import java.util.List;
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
        String configSha256,
        String gateStatus,
        List<CoverageWarningDetails> warnings,
        List<ComponentCoverageDetails> components,
        Instant createdAt) {

    public CoverageReportDetails {
        gateStatus = gateStatus == null ? "not_evaluated" : gateStatus;
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        components = List.copyOf(components == null ? List.of() : components);
    }

    public CoverageReportDetails(
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
        this(
                uploadId,
                repositoryId,
                commitSha,
                branch,
                pullRequestNumber,
                status,
                line,
                branchCoverage,
                function,
                statement,
                normalizedStorageBucket,
                normalizedStoragePath,
                null,
                "not_evaluated",
                List.of(),
                List.of(),
                createdAt);
    }
}
