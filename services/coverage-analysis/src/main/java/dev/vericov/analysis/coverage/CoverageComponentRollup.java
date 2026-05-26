package dev.vericov.analysis.coverage;

import java.math.BigDecimal;
import java.util.UUID;

public record CoverageComponentRollup(
        UUID componentId,
        String owner,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement,
        int gapCount,
        int debtCount,
        BigDecimal riskScoreTotal,
        String highestActiveRiskLevel) {

    public CoverageComponentRollup(
            UUID componentId,
            String owner,
            CoverageMetric line,
            CoverageMetric branch,
            CoverageMetric function,
            CoverageMetric statement) {
        this(componentId, owner, line, branch, function, statement, 0, 0, BigDecimal.ZERO, null);
    }

    public CoverageComponentRollup(
            UUID componentId,
            String owner,
            CoverageMetric line,
            CoverageMetric branch,
            CoverageMetric function,
            CoverageMetric statement,
            int gapCount,
            int debtCount,
            BigDecimal riskScoreTotal) {
        this(componentId, owner, line, branch, function, statement, gapCount, debtCount, riskScoreTotal, null);
    }
}
