package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardPullRequestDiff(
        UUID id,
        int pullRequestNumber,
        String baseSha,
        String headSha,
        String status,
        CoverageMetricDetails patchLine,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        Instant createdAt,
        UUID coverageReportId,
        BigDecimal projectLinePct) {
}
