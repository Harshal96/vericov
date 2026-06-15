package dev.vericov.analysis.coverage;

import dev.vericov.analysis.gaps.CoverageGapFinding;
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
        List<CoverageComponentRollup> componentRollups,
        List<CoverageGapFinding> gapFindings,
        String normalizedStorageBucket,
        String normalizedStoragePath,
        String configSha256,
        String gateStatus,
        List<String> warnings,
        Instant generatedAt) {

    public CoverageReport {
        files = List.copyOf(files == null ? List.of() : files);
        lineHits = List.copyOf(lineHits == null ? List.of() : lineHits);
        componentRollups = List.copyOf(componentRollups == null ? List.of() : componentRollups);
        gapFindings = List.copyOf(gapFindings == null ? List.of() : gapFindings);
        gateStatus = gateStatus == null ? "not_evaluated" : gateStatus;
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
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
                List.of(),
                List.of(),
                null,
                null,
                null,
                "not_evaluated",
                List.of(),
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
                componentRollups,
                gapFindings,
                bucket,
                path,
                configSha256,
                gateStatus,
                warnings,
                generatedAt);
    }

    public CoverageReport withResolvedFiles(List<CoverageFileSummary> resolvedFiles) {
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
                resolvedFiles,
                lineHits,
                componentRollups,
                gapFindings,
                normalizedStorageBucket,
                normalizedStoragePath,
                configSha256,
                gateStatus,
                warnings,
                generatedAt);
    }

    public CoverageReport withComponentRollups(List<CoverageComponentRollup> rollups) {
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
                rollups,
                gapFindings,
                normalizedStorageBucket,
                normalizedStoragePath,
                configSha256,
                gateStatus,
                warnings,
                generatedAt);
    }

    public CoverageReport withGapFindings(List<CoverageGapFinding> findings) {
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
                componentRollups,
                findings,
                normalizedStorageBucket,
                normalizedStoragePath,
                configSha256,
                gateStatus,
                warnings,
                generatedAt);
    }

    public CoverageReport withConfigSha256(String hash) {
        return copy(files, componentRollups, gapFindings, normalizedStorageBucket, normalizedStoragePath, hash, gateStatus, warnings);
    }

    public CoverageReport withComponentCoverage(
            List<CoverageFileSummary> resolvedFiles,
            List<CoverageComponentRollup> rollups,
            List<String> reportWarnings) {
        return copy(
                resolvedFiles,
                rollups,
                gapFindings,
                normalizedStorageBucket,
                normalizedStoragePath,
                configSha256,
                gateStatus,
                reportWarnings);
    }

    public CoverageReport withGateStatus(String status) {
        return copy(
                files,
                componentRollups,
                gapFindings,
                normalizedStorageBucket,
                normalizedStoragePath,
                configSha256,
                status,
                warnings);
    }

    private CoverageReport copy(
            List<CoverageFileSummary> copiedFiles,
            List<CoverageComponentRollup> rollups,
            List<CoverageGapFinding> findings,
            String bucket,
            String path,
            String hash,
            String status,
            List<String> reportWarnings) {
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
                copiedFiles,
                lineHits,
                rollups,
                findings,
                bucket,
                path,
                hash,
                status,
                reportWarnings,
                generatedAt);
    }
}
