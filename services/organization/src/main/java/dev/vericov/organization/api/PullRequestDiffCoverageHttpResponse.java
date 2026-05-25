package dev.vericov.organization.api;

import dev.vericov.organization.application.PullRequestDiffCoverageDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PullRequestDiffCoverageHttpResponse(
        UUID id,
        @JsonbProperty("coverage_report_id")
        UUID coverageReportId,
        @JsonbProperty("base_sha")
        String baseSha,
        @JsonbProperty("head_sha")
        String headSha,
        String status,
        @JsonbProperty("patch_line")
        CoverageMetricHttpResponse patchLine,
        @JsonbProperty("newly_missed_line_count")
        int newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count")
        int lostCoverageLineCount,
        List<DiffCoverageFileHttpResponse> files,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static PullRequestDiffCoverageHttpResponse from(PullRequestDiffCoverageDetails details) {
        return new PullRequestDiffCoverageHttpResponse(
                details.id(),
                details.coverageReportId(),
                details.baseSha(),
                details.headSha(),
                details.status(),
                CoverageMetricHttpResponse.from(details.patchLine()),
                details.newlyMissedLineCount(),
                details.lostCoverageLineCount(),
                details.files().stream().map(DiffCoverageFileHttpResponse::from).toList(),
                details.createdAt(),
                details.updatedAt());
    }
}
