package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageReportDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CoverageReportHttpResponse(
        @JsonbProperty("upload_id") UUID uploadId,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        String status,
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage") CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        @JsonbProperty("normalized_storage_bucket") String normalizedStorageBucket,
        @JsonbProperty("normalized_storage_path") String normalizedStoragePath,
        @JsonbProperty("config_sha256") String configSha256,
        @JsonbProperty("gate_status") String gateStatus,
        List<CoverageWarningHttpResponse> warnings,
        List<ComponentCoverageHttpResponse> components,
        @JsonbProperty("created_at") Instant createdAt) {

    public static CoverageReportHttpResponse from(CoverageReportDetails report) {
        return new CoverageReportHttpResponse(
                report.uploadId(),
                report.repositoryId(),
                report.commitSha(),
                report.branch(),
                report.pullRequestNumber(),
                report.status(),
                CoverageMetricHttpResponse.from(report.line()),
                CoverageMetricHttpResponse.from(report.branchCoverage()),
                CoverageMetricHttpResponse.from(report.function()),
                CoverageMetricHttpResponse.from(report.statement()),
                report.normalizedStorageBucket(),
                report.normalizedStoragePath(),
                report.configSha256(),
                report.gateStatus(),
                report.warnings().stream().map(CoverageWarningHttpResponse::from).toList(),
                report.components().stream().map(ComponentCoverageHttpResponse::from).toList(),
                report.createdAt());
    }
}
