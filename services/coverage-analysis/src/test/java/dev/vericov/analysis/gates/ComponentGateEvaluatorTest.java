package dev.vericov.analysis.gates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vericov.analysis.coverage.ComponentCoverageCalculator;
import dev.vericov.analysis.coverage.ComponentCoverageCalculatorTest;
import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComponentGateEvaluatorTest {
    @Test
    void evaluatesInheritedAndOverriddenGatesIncludingZeroMetrics() {
        CoverageMetric line = new CoverageMetric(8, 10);
        CoverageReport base = new CoverageReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "abc123",
                "main",
                null,
                line,
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                line,
                List.of(new CoverageFileSummary(
                        "services/payments/api/App.java",
                        line,
                        new CoverageMetric(0, 0),
                        new CoverageMetric(0, 0),
                        line)),
                List.of(),
                Instant.parse("2026-06-15T00:00:00Z"));
        CoverageReport report = new ComponentCoverageCalculator()
                .calculate(base, ComponentCoverageCalculatorTest.snapshot());

        List<GateEvaluation> evaluations = new ComponentGateEvaluator().evaluate(
                report,
                report.configSha256(),
                report.generatedAt());

        GateEvaluation apiLine = evaluations.stream()
                .filter(evaluation -> "payments-api".equals(evaluation.scopeKey()))
                .filter(evaluation -> "line".equals(evaluation.metric()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("80.0000"), apiLine.actual());
        assertEquals(new BigDecimal("90"), apiLine.threshold());
        assertEquals("failed", apiLine.status());
        assertEquals("component_config", apiLine.source());
        assertEquals(List.of("commerce", "payments", "payments-api"), apiLine.scopePath());

        GateEvaluation webLine = evaluations.stream()
                .filter(evaluation -> "payments-web".equals(evaluation.scopeKey()))
                .filter(evaluation -> "line".equals(evaluation.metric()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("100.0000"), webLine.actual());
        assertEquals("passed", webLine.status());
    }
}
