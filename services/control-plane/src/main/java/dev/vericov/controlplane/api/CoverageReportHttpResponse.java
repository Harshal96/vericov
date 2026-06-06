package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageReportDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CoverageReportHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("upload_id")
        UUID uploadId,
        @JsonbProperty("commit_sha")
        String commitSha,
        String branch,
        @JsonbProperty("pull_request_number")
        Integer pullRequestNumber,
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage")
        CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        List<CoverageFileSummaryHttpResponse> files,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static CoverageReportHttpResponse from(CoverageReportDetails details) {
        return new CoverageReportHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.uploadId(),
                details.commitSha(),
                details.branch(),
                details.pullRequestNumber(),
                CoverageMetricHttpResponse.from(details.line()),
                CoverageMetricHttpResponse.from(details.branchMetric()),
                CoverageMetricHttpResponse.from(details.function()),
                CoverageMetricHttpResponse.from(details.statement()),
                details.files().stream().map(CoverageFileSummaryHttpResponse::from).toList(),
                details.createdAt(),
                details.updatedAt());
    }
}
