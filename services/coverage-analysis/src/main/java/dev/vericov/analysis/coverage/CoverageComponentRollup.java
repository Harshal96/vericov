package dev.vericov.analysis.coverage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CoverageComponentRollup(
        String componentKey,
        String parentComponentKey,
        List<String> componentPath,
        int depth,
        int position,
        String name,
        List<String> owners,
        Map<String, BigDecimal> effectiveGates,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement,
        int directFileCount,
        int descendantFileCount,
        int gapCount,
        int debtCount,
        BigDecimal riskScoreTotal,
        String highestActiveRiskLevel) {

    public CoverageComponentRollup {
        componentPath = List.copyOf(componentPath == null ? List.of(componentKey) : componentPath);
        owners = List.copyOf(owners == null ? List.of() : owners);
        effectiveGates = Map.copyOf(effectiveGates == null ? Map.of() : effectiveGates);
        riskScoreTotal = riskScoreTotal == null ? BigDecimal.ZERO : riskScoreTotal;
    }

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

    public CoverageComponentRollup(
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
        this(
                componentId.toString(),
                null,
                List.of(componentId.toString()),
                0,
                0,
                componentId.toString(),
                owner == null ? List.of() : List.of(owner),
                Map.of(),
                line,
                branch,
                function,
                statement,
                0,
                0,
                gapCount,
                debtCount,
                riskScoreTotal,
                highestActiveRiskLevel);
    }

    public UUID componentId() {
        try {
            return UUID.fromString(componentKey);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String owner() {
        return owners.isEmpty() ? "unowned" : owners.getFirst();
    }

    public CoverageComponentRollup withRisk(
            int gapCount,
            int debtCount,
            BigDecimal riskScoreTotal,
            String highestActiveRiskLevel) {
        return new CoverageComponentRollup(
                componentKey,
                parentComponentKey,
                componentPath,
                depth,
                position,
                name,
                owners,
                effectiveGates,
                line,
                branch,
                function,
                statement,
                directFileCount,
                descendantFileCount,
                gapCount,
                debtCount,
                riskScoreTotal,
                highestActiveRiskLevel);
    }
}
