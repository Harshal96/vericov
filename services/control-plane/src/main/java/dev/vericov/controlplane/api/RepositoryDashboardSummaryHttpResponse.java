package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryDashboardSummaryDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RepositoryDashboardSummaryHttpResponse(
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("full_name")
        String fullName,
        String branch,
        @JsonbProperty("latest_commit_sha")
        String latestCommitSha,
        @JsonbProperty("latest_line_coverage")
        BigDecimal latestLineCoverage,
        @JsonbProperty("failing_gate_count")
        int failingGateCount,
        @JsonbProperty("latest_report_at")
        Instant latestReportAt) {

    public static RepositoryDashboardSummaryHttpResponse from(RepositoryDashboardSummaryDetails details) {
        return new RepositoryDashboardSummaryHttpResponse(
                details.repositoryId(),
                details.fullName(),
                details.branch(),
                details.latestCommitSha(),
                details.latestLineCoverage(),
                details.failingGateCount(),
                details.latestReportAt());
    }
}
