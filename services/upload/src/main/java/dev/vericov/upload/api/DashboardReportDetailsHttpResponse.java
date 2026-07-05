package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardReportDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record DashboardReportDetailsHttpResponse(
        UUID id,
        @JsonbProperty("upload_id") UUID uploadId,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        String status,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("line_covered") int lineCovered,
        @JsonbProperty("line_total") int lineTotal,
        @JsonbProperty("branch_covered") int branchCovered,
        @JsonbProperty("branch_total") int branchTotal,
        @JsonbProperty("function_covered") int functionCovered,
        @JsonbProperty("function_total") int functionTotal,
        @JsonbProperty("statement_covered") int statementCovered,
        @JsonbProperty("statement_total") int statementTotal,
        DashboardRepositoryHttpResponse repository) {
    public static DashboardReportDetailsHttpResponse from(DashboardReportDetails details) {
        var report = details.report();
        return new DashboardReportDetailsHttpResponse(
                report.id(),
                report.uploadId(),
                report.repositoryId(),
                report.commitSha(),
                report.branch(),
                report.pullRequestNumber(),
                report.status(),
                report.createdAt(),
                report.lineCovered(),
                report.lineTotal(),
                report.branchCovered(),
                report.branchTotal(),
                report.functionCovered(),
                report.functionTotal(),
                report.statementCovered(),
                report.statementTotal(),
                DashboardRepositoryHttpResponse.from(details.repository()));
    }
}
