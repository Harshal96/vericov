package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateEvaluatorTest {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID ORG_ID = UUID.fromString("2ca9c094-7c28-4cb9-9b99-aae95cf07050");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID REPORT_ID = UUID.fromString("46d061c7-b160-4550-b7a4-5e0b81821621");
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void evaluatesActiveProjectCoverageGates() {
        GateEvaluator evaluator = new GateEvaluator();

        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(
                gate("line-minimum", "project_coverage", "line", "80.0", true, "active"),
                gate("branch-minimum", "project_coverage", "branch", "80.0", true, "active"),
                gate("statement-advisory", "project_coverage", "statement", "95.0", false, "active"),
                gate("disabled", "project_coverage", "line", "100.0", true, "disabled"),
                gate("patch", "patch_coverage", "line", "90.0", true, "active")), NOW);

        assertEquals(3, evaluations.size());

        GateEvaluation line = evaluations.get(0);
        assertEquals("line-minimum", line.gateName());
        assertEquals("passed", line.status());
        assertEquals(new BigDecimal("80.0000"), line.threshold());
        assertEquals(new BigDecimal("85.0000"), line.actual());
        assertEquals(REPORT_ID, line.coverageReportId());
        assertEquals("project", line.details().get("scope"));
        assertEquals(17, line.details().get("covered"));
        assertEquals(20, line.details().get("total"));

        GateEvaluation branch = evaluations.get(1);
        assertEquals("branch-minimum", branch.gateName());
        assertEquals("failed", branch.status());
        assertEquals(new BigDecimal("50.0000"), branch.actual());

        GateEvaluation statement = evaluations.get(2);
        assertEquals("statement-advisory", statement.gateName());
        assertEquals("warning", statement.status());
        assertEquals(new BigDecimal("85.0000"), statement.actual());
    }

    @Test
    void emitsWarningWhenProjectGateUsesUnsupportedReportMetric() {
        GateEvaluator evaluator = new GateEvaluator();

        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(
                gate("mutation", "project_coverage", "mutation", "80.0", true, "active")), NOW);

        assertEquals(1, evaluations.size());
        GateEvaluation evaluation = evaluations.getFirst();
        assertEquals("warning", evaluation.status());
        assertNull(evaluation.actual());
        assertEquals("metric_not_available_in_coverage_report", evaluation.details().get("reason"));
    }

    @Test
    void preservesJsonNullValuesInConfigurationAndEvaluationDetails() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("optional", null);
        GateConfiguration gate = new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                "line-minimum",
                "project_coverage",
                "line",
                new BigDecimal("80.0"),
                null,
                true,
                config,
                "active");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("optional", null);
        GateEvaluation evaluation = new GateEvaluation(
                UUID.fromString("76bbb788-221e-42e6-8e36-b54be561a018"),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                REPORT_ID,
                "abc123",
                "main",
                42,
                "line-minimum",
                "project_coverage",
                "line",
                new BigDecimal("80.0000"),
                new BigDecimal("85.0000"),
                "passed",
                true,
                details,
                NOW);

        assertTrue(gate.config().containsKey("optional"));
        assertNull(gate.config().get("optional"));
        assertTrue(evaluation.details().containsKey("optional"));
        assertNull(evaluation.details().get("optional"));
    }

    private static GateConfiguration gate(
            String name,
            String gateType,
            String metric,
            String threshold,
            boolean blocking,
            String status) {
        return new GateConfiguration(
                UUID.nameUUIDFromBytes(name.getBytes()),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                name,
                gateType,
                metric,
                new BigDecimal(threshold),
                null,
                blocking,
                Map.of(),
                status);
    }

    private static CoverageReport report() {
        return new CoverageReport(
                REPORT_ID,
                UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6"),
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                new CoverageMetric(17, 20),
                new CoverageMetric(1, 2),
                new CoverageMetric(3, 3),
                new CoverageMetric(17, 20),
                List.of(new CoverageFileSummary(
                        "src/App.java",
                        new CoverageMetric(17, 20),
                        new CoverageMetric(1, 2),
                        new CoverageMetric(3, 3),
                        new CoverageMetric(17, 20))),
                List.of(),
                NOW);
    }
}
