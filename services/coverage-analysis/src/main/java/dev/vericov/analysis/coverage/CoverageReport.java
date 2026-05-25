package dev.vericov.analysis.coverage;

import java.time.Instant;
import java.util.List;
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
        Instant generatedAt) {

    public CoverageReport {
        files = List.copyOf(files == null ? List.of() : files);
        lineHits = List.copyOf(lineHits == null ? List.of() : lineHits);
    }
}
