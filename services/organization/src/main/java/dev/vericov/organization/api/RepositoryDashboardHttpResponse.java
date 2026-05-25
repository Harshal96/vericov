package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryDashboardDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record RepositoryDashboardHttpResponse(
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String branch,
        @JsonbProperty("latest_commit_sha")
        String latestCommitSha,
        @JsonbProperty("latest_line_coverage")
        CoverageMetricHttpResponse latestLineCoverage,
        @JsonbProperty("latest_branch_coverage")
        CoverageMetricHttpResponse latestBranchCoverage,
        @JsonbProperty("latest_function_coverage")
        CoverageMetricHttpResponse latestFunctionCoverage,
        @JsonbProperty("latest_statement_coverage")
        CoverageMetricHttpResponse latestStatementCoverage,
        @JsonbProperty("failing_gate_count")
        int failingGateCount,
        @JsonbProperty("latest_report_at")
        Instant latestReportAt) {

    public static RepositoryDashboardHttpResponse from(RepositoryDashboardDetails details) {
        return new RepositoryDashboardHttpResponse(
                details.organizationId(),
                details.repositoryId(),
                details.branch(),
                details.latestCommitSha(),
                CoverageMetricHttpResponse.from(details.latestLineCoverage()),
                CoverageMetricHttpResponse.from(details.latestBranchCoverage()),
                CoverageMetricHttpResponse.from(details.latestFunctionCoverage()),
                CoverageMetricHttpResponse.from(details.latestStatementCoverage()),
                details.failingGateCount(),
                details.latestReportAt());
    }
}
