package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PullRequestDiffCoverageDetails(
        UUID id,
        UUID coverageReportId,
        String baseSha,
        String headSha,
        String status,
        CoverageMetricDetails patchLine,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        List<DiffCoverageFileDetails> files,
        Instant createdAt,
        Instant updatedAt) {

    public PullRequestDiffCoverageDetails {
        files = List.copyOf(files == null ? List.of() : files);
    }
}
