package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardRepositoryOverview;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardRepositoryOverviewHttpResponse(
        UUID id,
        @JsonbProperty("full_name") String fullName,
        String provider,
        @JsonbProperty("default_branch") String defaultBranch,
        String visibility,
        String status,
        @JsonbProperty("updated_at") Instant updatedAt,
        @JsonbProperty("report_id") UUID reportId,
        @JsonbProperty("commit_sha") String commitSha,
        @JsonbProperty("report_created_at") Instant reportCreatedAt,
        @JsonbProperty("line_covered") Integer lineCovered,
        @JsonbProperty("line_total") Integer lineTotal,
        @JsonbProperty("branch_covered") Integer branchCovered,
        @JsonbProperty("branch_total") Integer branchTotal,
        @JsonbProperty("function_covered") Integer functionCovered,
        @JsonbProperty("function_total") Integer functionTotal,
        @JsonbProperty("statement_covered") Integer statementCovered,
        @JsonbProperty("statement_total") Integer statementTotal,
        @JsonbProperty("line_delta") BigDecimal lineDelta,
        @JsonbProperty("report_count") long reportCount,
        @JsonbProperty("active_gaps") long activeGaps,
        @JsonbProperty("failing_gates") long failingGates) {
    public static DashboardRepositoryOverviewHttpResponse from(DashboardRepositoryOverview repository) {
        return new DashboardRepositoryOverviewHttpResponse(
                repository.id(),
                repository.fullName(),
                repository.provider(),
                repository.defaultBranch(),
                repository.visibility(),
                repository.status(),
                repository.updatedAt(),
                repository.reportId(),
                repository.commitSha(),
                repository.reportCreatedAt(),
                repository.lineCovered(),
                repository.lineTotal(),
                repository.branchCovered(),
                repository.branchTotal(),
                repository.functionCovered(),
                repository.functionTotal(),
                repository.statementCovered(),
                repository.statementTotal(),
                repository.lineDelta(),
                repository.reportCount(),
                repository.activeGaps(),
                repository.failingGates());
    }
}
