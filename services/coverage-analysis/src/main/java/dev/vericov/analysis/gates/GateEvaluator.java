package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GateEvaluator {

    public List<GateEvaluation> evaluate(
            CoverageReport report,
            List<GateConfiguration> gates,
            Instant evaluatedAt) {
        return gates.stream()
                .filter(GateConfiguration::active)
                .filter(gate -> "project_coverage".equals(gate.gateType()))
                .map(gate -> evaluateProjectCoverage(report, gate, evaluatedAt))
                .toList();
    }

    private static GateEvaluation evaluateProjectCoverage(
            CoverageReport report,
            GateConfiguration gate,
            Instant evaluatedAt) {
        Optional<CoverageMetric> metric = reportMetric(report, gate.metric());
        if (metric.isEmpty()) {
            return evaluation(report, gate, null, "warning", Map.of(
                    "scope", "project",
                    "reason", "metric_not_available_in_coverage_report",
                    "coverage_report_id", report.reportId().toString()), evaluatedAt);
        }

        CoverageMetric coverageMetric = metric.get();
        BigDecimal actual = percentage(coverageMetric);
        String status = actual.compareTo(normalize(gate.threshold())) >= 0
                ? "passed"
                : gate.blocking() ? "failed" : "warning";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "project");
        details.put("coverage_report_id", report.reportId().toString());
        details.put("covered", coverageMetric.covered());
        details.put("total", coverageMetric.total());
        details.put("percentage", actual);
        details.put("threshold", normalize(gate.threshold()));
        return evaluation(report, gate, actual, status, details, evaluatedAt);
    }

    private static GateEvaluation evaluation(
            CoverageReport report,
            GateConfiguration gate,
            BigDecimal actual,
            String status,
            Map<String, Object> details,
            Instant evaluatedAt) {
        return new GateEvaluation(
                UUID.randomUUID(),
                report.tenantId(),
                gate.organizationId(),
                report.repositoryId(),
                report.reportId(),
                report.commitSha(),
                report.branchName(),
                report.pullRequestNumber(),
                gate.name(),
                gate.gateType(),
                gate.metric(),
                normalize(gate.threshold()),
                actual,
                status,
                gate.blocking(),
                details,
                evaluatedAt);
    }

    private static Optional<CoverageMetric> reportMetric(CoverageReport report, String metric) {
        return switch (metric) {
            case "line" -> Optional.of(report.line());
            case "branch" -> Optional.of(report.branch());
            case "function" -> Optional.of(report.function());
            case "statement" -> Optional.of(report.statement());
            default -> Optional.empty();
        };
    }

    private static BigDecimal percentage(CoverageMetric metric) {
        return BigDecimal.valueOf(metric.percentage()).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
