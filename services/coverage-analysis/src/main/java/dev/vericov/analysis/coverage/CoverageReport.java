package dev.vericov.analysis.coverage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CoverageReport(
        UUID reportId,
        UUID uploadId,
        UUID tenantId,
        UUID repositoryId,
        String commitSha,
        String branchName,
        Integer pullRequestNumber,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement,
        List<CoverageFileSummary> files,
        List<CoverageLineHit> lineHits,
        String normalizedStorageBucket,
        String normalizedStoragePath,
        Instant generatedAt) {

    public CoverageReport {
        files = List.copyOf(files == null ? List.of() : files);
        lineHits = List.copyOf(lineHits == null ? List.of() : lineHits);
    }

    public CoverageReport(
            UUID reportId,
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String commitSha,
            String branchName,
            Integer pullRequestNumber,
            CoverageMetric line,
            CoverageMetric branch,
            CoverageMetric function,
            CoverageMetric statement,
            List<CoverageFileSummary> files,
            List<CoverageLineHit> lineHits,
            Instant generatedAt) {
        this(
                reportId,
                uploadId,
                tenantId,
                repositoryId,
                commitSha,
                branchName,
                pullRequestNumber,
                line,
                branch,
                function,
                statement,
                files,
                lineHits,
                null,
                null,
                generatedAt);
    }

    public CoverageReport withNormalizedStorage(String bucket, String path) {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(path, "path");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        return new CoverageReport(
                reportId,
                uploadId,
                tenantId,
                repositoryId,
                commitSha,
                branchName,
                pullRequestNumber,
                line,
                branch,
                function,
                statement,
                files,
                lineHits,
                bucket,
                path,
                generatedAt);
    }
}
