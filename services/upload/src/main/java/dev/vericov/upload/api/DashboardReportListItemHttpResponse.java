package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardReportListItem;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardReportListItemHttpResponse(
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
        @JsonbProperty("line_delta") BigDecimal lineDelta,
        @JsonbProperty("ci_provider") String ciProvider,
        @JsonbProperty("ci_build_url") String ciBuildUrl,
        List<String> flags) {
    public static DashboardReportListItemHttpResponse from(DashboardReportListItem item) {
        var report = item.report();
        return new DashboardReportListItemHttpResponse(
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
                item.lineDelta(),
                item.ciProvider(),
                item.ciBuildUrl(),
                item.flags());
    }
}
