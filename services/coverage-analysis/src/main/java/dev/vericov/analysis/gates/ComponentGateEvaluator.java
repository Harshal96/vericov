package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageComponentRollup;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ComponentGateEvaluator {
    public List<GateEvaluation> evaluate(
            CoverageReport report,
            String configSha256,
            Instant evaluatedAt) {
        List<GateEvaluation> evaluations = new ArrayList<>();
        for (CoverageComponentRollup rollup : report.componentRollups()) {
            if ("unassigned".equals(rollup.componentKey())) {
                continue;
            }
            rollup.effectiveGates().forEach((metric, threshold) -> {
                CoverageMetric coverage = metric(rollup, metric);
                BigDecimal actual = percentage(coverage);
                String status = actual.compareTo(threshold) >= 0 ? "passed" : "failed";
                evaluations.add(new GateEvaluation(
                        UUID.randomUUID(),
                        report.tenantId(),
                        report.repositoryId(),
                        report.reportId(),
                        report.commitSha(),
                        report.branchName(),
                        report.pullRequestNumber(),
                        rollup.componentKey() + "-" + metric,
                        "component_coverage",
                        metric,
                        threshold,
                        actual,
                        status,
                        true,
                        Map.of(
                                "covered", coverage.covered(),
                                "total", coverage.total(),
                                "percentage", actual,
                                "threshold", threshold,
                                "config_sha256", configSha256 == null ? "" : configSha256),
                        "component_config",
                        "component",
                        rollup.componentKey(),
                        rollup.componentPath(),
                        evaluatedAt));
            });
        }
        return List.copyOf(evaluations);
    }

    private static CoverageMetric metric(CoverageComponentRollup rollup, String metric) {
        return switch (metric) {
            case "line" -> rollup.line();
            case "branch" -> rollup.branch();
            case "function" -> rollup.function();
            case "statement" -> rollup.statement();
            default -> throw new IllegalArgumentException("Unsupported component gate metric: " + metric);
        };
    }

    private static BigDecimal percentage(CoverageMetric metric) {
        if (metric.total() == 0) {
            return BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(metric.covered())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(metric.total()), 4, RoundingMode.HALF_UP);
    }
}
