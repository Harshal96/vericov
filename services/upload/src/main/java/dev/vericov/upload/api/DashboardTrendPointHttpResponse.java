package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardTrendPoint;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardTrendPointHttpResponse(
        @JsonbProperty("report_id") UUID reportId,
        @JsonbProperty("commit_sha") String commitSha,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("line_pct") BigDecimal linePct,
        @JsonbProperty("branch_pct") BigDecimal branchPct,
        @JsonbProperty("function_pct") BigDecimal functionPct,
        @JsonbProperty("statement_pct") BigDecimal statementPct) {
    public static DashboardTrendPointHttpResponse from(DashboardTrendPoint point) {
        return new DashboardTrendPointHttpResponse(
                point.reportId(),
                point.commitSha(),
                point.createdAt(),
                point.linePct(),
                point.branchPct(),
                point.functionPct(),
                point.statementPct());
    }
}
