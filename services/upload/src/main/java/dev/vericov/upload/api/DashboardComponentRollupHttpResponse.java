package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardComponentRollup;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;

public record DashboardComponentRollupHttpResponse(
        @JsonbProperty("component_id") String componentId,
        String owner,
        @JsonbProperty("line_covered") long lineCovered,
        @JsonbProperty("line_total") long lineTotal,
        @JsonbProperty("branch_covered") long branchCovered,
        @JsonbProperty("branch_total") long branchTotal,
        @JsonbProperty("function_covered") long functionCovered,
        @JsonbProperty("function_total") long functionTotal,
        @JsonbProperty("statement_covered") long statementCovered,
        @JsonbProperty("statement_total") long statementTotal,
        @JsonbProperty("gap_count") long gapCount,
        @JsonbProperty("debt_count") long debtCount,
        @JsonbProperty("risk_score_total") BigDecimal riskScoreTotal,
        @JsonbProperty("highest_active_risk_level") String highestActiveRiskLevel) {

    static DashboardComponentRollupHttpResponse from(DashboardComponentRollup component) {
        return new DashboardComponentRollupHttpResponse(
                component.componentId(),
                component.owner(),
                component.line().covered(),
                component.line().total(),
                component.branchCoverage().covered(),
                component.branchCoverage().total(),
                component.function().covered(),
                component.function().total(),
                component.statement().covered(),
                component.statement().total(),
                component.gapCount(),
                component.debtCount(),
                component.riskScoreTotal(),
                component.highestActiveRiskLevel());
    }
}
