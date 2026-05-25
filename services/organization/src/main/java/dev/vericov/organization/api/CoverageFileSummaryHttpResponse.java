package dev.vericov.organization.api;

import dev.vericov.organization.application.CoverageFileSummaryDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record CoverageFileSummaryHttpResponse(
        UUID id,
        @JsonbProperty("coverage_report_id")
        UUID coverageReportId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("commit_sha")
        String commitSha,
        @JsonbProperty("file_path")
        String filePath,
        CoverageMetricHttpResponse line,
        CoverageMetricHttpResponse branch,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        @JsonbProperty("created_at")
        Instant createdAt) {

    public static CoverageFileSummaryHttpResponse from(CoverageFileSummaryDetails details) {
        return new CoverageFileSummaryHttpResponse(
                details.id(),
                details.coverageReportId(),
                details.repositoryId(),
                details.commitSha(),
                details.filePath(),
                CoverageMetricHttpResponse.from(details.line()),
                CoverageMetricHttpResponse.from(details.branch()),
                CoverageMetricHttpResponse.from(details.function()),
                CoverageMetricHttpResponse.from(details.statement()),
                details.createdAt());
    }
}
