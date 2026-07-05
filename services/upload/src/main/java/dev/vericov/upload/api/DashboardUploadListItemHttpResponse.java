package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardUploadListItem;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record DashboardUploadListItemHttpResponse(
        UUID id,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        @JsonbProperty("ci_provider") String ciProvider,
        @JsonbProperty("ci_build_url") String ciBuildUrl,
        String status,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("completed_at") Instant completedAt,
        @JsonbProperty("coverage_report_id") UUID coverageReportId,
        @JsonbProperty("error_code") String errorCode,
        @JsonbProperty("error_message") String errorMessage) {
    public static DashboardUploadListItemHttpResponse from(DashboardUploadListItem item) {
        return new DashboardUploadListItemHttpResponse(
                item.id(),
                item.repositoryId(),
                item.commitSha(),
                item.branch(),
                item.pullRequestNumber(),
                item.ciProvider(),
                item.ciBuildUrl(),
                item.status(),
                item.createdAt(),
                item.completedAt(),
                item.coverageReportId(),
                item.errorCode(),
                item.errorMessage());
    }
}
