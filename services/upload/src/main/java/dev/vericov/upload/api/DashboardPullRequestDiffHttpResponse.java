package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardPullRequestDiff;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardPullRequestDiffHttpResponse(
        UUID id,
        @JsonbProperty("pull_request_number") int pullRequestNumber,
        @JsonbProperty("base_sha") String baseSha,
        @JsonbProperty("head_sha") String headSha,
        String status,
        @JsonbProperty("patch_line_covered") long patchLineCovered,
        @JsonbProperty("patch_line_total") long patchLineTotal,
        @JsonbProperty("newly_missed_line_count") int newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count") int lostCoverageLineCount,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("coverage_report_id") UUID coverageReportId,
        @JsonbProperty("project_line_pct") BigDecimal projectLinePct) {
    public static DashboardPullRequestDiffHttpResponse from(DashboardPullRequestDiff diff) {
        return new DashboardPullRequestDiffHttpResponse(
                diff.id(),
                diff.pullRequestNumber(),
                diff.baseSha(),
                diff.headSha(),
                diff.status(),
                diff.patchLine().covered(),
                diff.patchLine().total(),
                diff.newlyMissedLineCount(),
                diff.lostCoverageLineCount(),
                diff.createdAt(),
                diff.coverageReportId(),
                diff.projectLinePct());
    }
}
