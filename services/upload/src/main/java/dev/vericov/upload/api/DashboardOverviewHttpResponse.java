package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardOverview;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;

public record DashboardOverviewHttpResponse(
        @JsonbProperty("repo_count") long repoCount,
        @JsonbProperty("active_repo_count") long activeRepoCount,
        @JsonbProperty("weighted_line_coverage") BigDecimal weightedLineCoverage,
        @JsonbProperty("total_reports") long totalReports,
        @JsonbProperty("active_gaps") long activeGaps,
        @JsonbProperty("critical_gaps") long criticalGaps,
        @JsonbProperty("failing_gates") long failingGates) {
    public static DashboardOverviewHttpResponse from(DashboardOverview overview) {
        return new DashboardOverviewHttpResponse(
                overview.repoCount(),
                overview.activeRepoCount(),
                overview.weightedLineCoverage(),
                overview.totalReports(),
                overview.activeGaps(),
                overview.criticalGaps(),
                overview.failingGates());
    }
}
