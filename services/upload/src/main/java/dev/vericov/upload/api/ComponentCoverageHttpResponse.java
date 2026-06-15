package dev.vericov.upload.api;

import dev.vericov.upload.application.ComponentCoverageDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ComponentCoverageHttpResponse(
        String key,
        String name,
        List<String> path,
        int depth,
        int position,
        List<String> owners,
        @JsonbProperty("effective_gates") Map<String, BigDecimal> effectiveGates,
        CoverageMetricHttpResponse line,
        @JsonbProperty("branch_coverage") CoverageMetricHttpResponse branchCoverage,
        CoverageMetricHttpResponse function,
        CoverageMetricHttpResponse statement,
        @JsonbProperty("direct_file_count") int directFileCount,
        @JsonbProperty("descendant_file_count") int descendantFileCount,
        @JsonbProperty("gap_count") int gapCount,
        @JsonbProperty("debt_count") int debtCount,
        @JsonbProperty("risk_score_total") BigDecimal riskScoreTotal,
        @JsonbProperty("highest_active_risk_level") String highestActiveRiskLevel,
        List<CoverageGateHttpResponse> gates,
        List<ComponentCoverageHttpResponse> components) {

    static ComponentCoverageHttpResponse from(ComponentCoverageDetails component) {
        return new ComponentCoverageHttpResponse(
                component.key(),
                component.name(),
                component.path(),
                component.depth(),
                component.position(),
                component.owners(),
                component.effectiveGates(),
                CoverageMetricHttpResponse.from(component.line()),
                CoverageMetricHttpResponse.from(component.branchCoverage()),
                CoverageMetricHttpResponse.from(component.function()),
                CoverageMetricHttpResponse.from(component.statement()),
                component.directFileCount(),
                component.descendantFileCount(),
                component.gapCount(),
                component.debtCount(),
                component.riskScoreTotal(),
                component.highestActiveRiskLevel(),
                component.gates().stream().map(CoverageGateHttpResponse::from).toList(),
                component.components().stream().map(ComponentCoverageHttpResponse::from).toList());
    }
}
