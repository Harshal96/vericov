package dev.vericov.upload.application;

import java.math.BigDecimal;

public record DashboardComponentRollup(
        String componentId,
        String owner,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement,
        long gapCount,
        long debtCount,
        BigDecimal riskScoreTotal,
        String highestActiveRiskLevel) {
}
