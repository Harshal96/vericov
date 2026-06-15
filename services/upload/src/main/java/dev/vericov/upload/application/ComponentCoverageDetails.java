package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ComponentCoverageDetails(
        String key,
        String name,
        List<String> path,
        int depth,
        int position,
        List<String> owners,
        Map<String, BigDecimal> effectiveGates,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement,
        int directFileCount,
        int descendantFileCount,
        int gapCount,
        int debtCount,
        BigDecimal riskScoreTotal,
        String highestActiveRiskLevel,
        List<CoverageGateDetails> gates,
        List<ComponentCoverageDetails> components) {

    public ComponentCoverageDetails {
        path = List.copyOf(path);
        owners = List.copyOf(owners);
        effectiveGates = Map.copyOf(effectiveGates);
        gates = List.copyOf(gates);
        components = List.copyOf(components);
    }
}
