package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
                gate("function-minimum", "project_coverage", "function", "100.0", true, "active"),
                gate("statement-advisory", "project_coverage", "statement", "95.0", false, "active"),
                gate("disabled", "project_coverage", "line", "100.0", true, "disabled"),
                gate("patch", "patch_coverage", "line", "90.0", true, "active")), NOW);

        assertEquals(4, evaluations.size());

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

        GateEvaluation function = evaluations.get(2);
        assertEquals("function-minimum", function.gateName());
        assertEquals("passed", function.status());
        assertEquals(new BigDecimal("100.0000"), function.actual());

        GateEvaluation statement = evaluations.get(3);
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

    private static GateConfiguration gateWithConfig(
            String name,
            String gateType,
            String metric,
            String threshold,
            BigDecimal maxDrop,
            boolean blocking,
            Map<String, Object> config,
            String status) {
        return new GateConfiguration(
                UUID.nameUUIDFromBytes(name.getBytes()),
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                name,
                gateType,
                metric,
                threshold == null ? null : new BigDecimal(threshold),
                maxDrop,
                blocking,
                config,
                status);
    }

    @Test
    void evaluatesAgentReviewGateWithDebt() {
        GateEvaluator evaluator = new GateEvaluator();

        // 1. No debt: High-risk active finding -> failed
        Finding finding = new Finding(UUID.randomUUID(), "src/App.java", 10, "high", "active");
        RepositoryContext contextNoDebt = new RepositoryContext("ctx-1", List.of(finding), List.of(), Map.of());

        GateConfiguration gate = gateWithConfig(
                "agent-review", "agent_review_required", "risk", "100.0", null, true,
                Map.of("debt", Map.of("mode", "suppress_findings", "allow_risk_levels", List.of("low", "medium"))),
                "active");

        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(gate), contextNoDebt, null, NOW);
        assertEquals(1, evaluations.size());
        assertEquals("failed", evaluations.get(0).status());
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());
        Map<?, ?> effective = (Map<?, ?>) evaluations.get(0).details().get("effective");
        assertEquals("unsuppressed_high_risk_findings_remain", effective.get("reason"));

        // 2. Active debt matching, but risk level high NOT allowed -> failed
        UUID debtId = UUID.randomUUID();
        DebtItem debt = new DebtItem(debtId, "src/App.java", 5, 15, "active", NOW.plusSeconds(3600));
        RepositoryContext contextWithDebtLowMedium = new RepositoryContext("ctx-2", List.of(finding), List.of(debt), Map.of());

        evaluations = evaluator.evaluate(report(), List.of(gate), contextWithDebtLowMedium, null, NOW);
        assertEquals("failed", evaluations.get(0).status());

        // 3. Active debt matching, risk level high allowed -> passed
        GateConfiguration gateHighAllowed = gateWithConfig(
                "agent-review", "agent_review_required", "risk", "100.0", null, true,
                Map.of("debt", Map.of("mode", "suppress_findings", "allow_risk_levels", List.of("low", "medium", "high"))),
                "active");
        evaluations = evaluator.evaluate(report(), List.of(gateHighAllowed), contextWithDebtLowMedium, null, NOW);
        assertEquals("passed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());
        Map<?, ?> debtDetails = (Map<?, ?>) evaluations.get(0).details().get("debt");
        assertEquals(List.of(finding.id().toString()), debtDetails.get("suppressed_finding_ids"));
        assertEquals(List.of(debtId.toString()), debtDetails.get("suppressed_debt_item_ids"));

        // 4. Expired debt matching, fail_on_expired_debt = true -> failed with reason expired_debt_reappeared
        DebtItem expiredDebt = new DebtItem(debtId, "src/App.java", 5, 15, "active", NOW.minusSeconds(3600));
        RepositoryContext contextExpired = new RepositoryContext("ctx-3", List.of(finding), List.of(expiredDebt), Map.of());
        evaluations = evaluator.evaluate(report(), List.of(gateHighAllowed), contextExpired, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
        effective = (Map<?, ?>) evaluations.get(0).details().get("effective");
        assertEquals("expired_debt_reappeared", effective.get("reason"));

        // 5. Expired debt matching, fail_on_expired_debt = false -> failed with reason unsuppressed_high_risk_findings_remain
        GateConfiguration gateHighAllowedNoFail = gateWithConfig(
                "agent-review", "agent_review_required", "risk", "100.0", null, true,
                Map.of("debt", Map.of("mode", "suppress_findings", "allow_risk_levels", List.of("low", "medium", "high"), "fail_on_expired_debt", false)),
                "active");
        evaluations = evaluator.evaluate(report(), List.of(gateHighAllowedNoFail), contextExpired, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
        effective = (Map<?, ?>) evaluations.get(0).details().get("effective");
        assertEquals("unsuppressed_high_risk_findings_remain", effective.get("reason"));
    }

    @Test
    void evaluatesProjectCoverageGateWithMetricAdjustment() {
        GateEvaluator evaluator = new GateEvaluator();

        // Report has total line: 20, covered: 17. Raw coverage is 85%. Threshold: 90%.
        // Uncovered line hits: line 2, 4, 6 in src/App.java have 0 hits.
        CoverageReport report = new CoverageReport(
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
                List.of(
                        new CoverageLineHit("src/App.java", 1, 5L),
                        new CoverageLineHit("src/App.java", 2, 0L),
                        new CoverageLineHit("src/App.java", 3, 3L),
                        new CoverageLineHit("src/App.java", 4, 0L),
                        new CoverageLineHit("src/App.java", 5, 2L),
                        new CoverageLineHit("src/App.java", 6, 0L)
                ),
                NOW);

        GateConfiguration gate = gateWithConfig(
                "project-line", "project_coverage", "line", "90.0", null, true,
                Map.of("debt", Map.of("mode", "adjust_metric")),
                "active");

        // 1. No debt -> raw coverage 85% < 90% -> fails
        RepositoryContext contextNoDebt = new RepositoryContext("ctx-1", List.of(), List.of(), Map.of());
        List<GateEvaluation> evaluations = evaluator.evaluate(report, List.of(gate), contextNoDebt, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(85).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());

        // 2. Active debt covering lines 2, 4, 6.
        // Waived lines = 3. adjustedTotal = 20 - 3 = 17. Effective = 17/17 = 100% -> passed
        UUID debtId = UUID.randomUUID();
        DebtItem debtApp = new DebtItem(debtId, "src/App.java", 1, 10, "active", NOW.plusSeconds(3600));
        RepositoryContext contextWithDebt = new RepositoryContext("ctx-2", List.of(), List.of(debtApp), Map.of());
        evaluations = evaluator.evaluate(report, List.of(gate), contextWithDebt, null, NOW);
        assertEquals("passed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());
        Map<?, ?> debtDetails = (Map<?, ?>) evaluations.get(0).details().get("debt");
        assertEquals(List.of(debtId.toString()), debtDetails.get("suppressed_debt_item_ids"));

        // 3. Expired debt covering lines -> fails
        DebtItem expiredDebt = new DebtItem(debtId, "src/App.java", 1, 10, "active", NOW.minusSeconds(3600));
        RepositoryContext contextExpired = new RepositoryContext("ctx-3", List.of(), List.of(expiredDebt), Map.of());
        evaluations = evaluator.evaluate(report, List.of(gate), contextExpired, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
        Map<?, ?> effective = (Map<?, ?>) evaluations.get(0).details().get("effective");
        assertEquals("expired_debt_reappeared", effective.get("reason"));
    }

    @Test
    void evaluatesPatchCoverageGateWithMetricAdjustment() {
        GateEvaluator evaluator = new GateEvaluator();

        // DiffCoverageReport has 10 patch lines, 8 covered, 80% coverage. Threshold: 85%.
        DiffCoverageLine line1 = new DiffCoverageLine("src/App.java", null, null, 10, DiffLineType.ADDED, true, null, 0L, true, false);
        DiffCoverageLine line2 = new DiffCoverageLine("src/App.java", null, null, 11, DiffLineType.ADDED, true, null, 0L, true, false);
        DiffCoverageFile file = new DiffCoverageFile("src/App.java", null, "modified", 8, 10, 2, 0, List.of(line1, line2));
        DiffCoverageReport diffCoverage = new DiffCoverageReport("baseSha", "headSha", 8, 10, 2, 0, List.of(file));

        GateConfiguration gate = gateWithConfig(
                "patch-line", "patch_coverage", "line", "85.0", null, true,
                Map.of("debt", Map.of("mode", "adjust_metric")),
                "active");

        // 1. No debt -> raw coverage 80% < 85% -> fails
        RepositoryContext contextNoDebt = new RepositoryContext("ctx-1", List.of(), List.of(), Map.of());
        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(gate), contextNoDebt, diffCoverage, NOW);
        assertEquals("failed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(80).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());

        // 2. Active debt covering src/App.java line 10 & 11
        UUID debtId = UUID.randomUUID();
        DebtItem debt = new DebtItem(debtId, "src/App.java", 10, 11, "active", NOW.plusSeconds(3600));
        RepositoryContext contextWithDebt = new RepositoryContext("ctx-2", List.of(), List.of(debt), Map.of());
        evaluations = evaluator.evaluate(report(), List.of(gate), contextWithDebt, diffCoverage, NOW);
        assertEquals("passed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());

        // 3. Expired debt -> fails
        DebtItem expiredDebt = new DebtItem(debtId, "src/App.java", 10, 11, "active", NOW.minusSeconds(3600));
        RepositoryContext contextExpired = new RepositoryContext("ctx-3", List.of(), List.of(expiredDebt), Map.of());
        evaluations = evaluator.evaluate(report(), List.of(gate), contextExpired, diffCoverage, NOW);
        assertEquals("failed", evaluations.get(0).status());
    }

    @Test
    void evaluatesCoverageDropGateWithMetricAdjustment() {
        GateEvaluator evaluator = new GateEvaluator();

        // Report has total line: 20, covered: 17. Raw coverage is 85%.
        // Base percentage is 90% (configured in gate config). Max drop is 3%.
        // Drop = 90% - 85% = 5% > 3% -> fails
        CoverageReport report = new CoverageReport(
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
                List.of(
                        new CoverageLineHit("src/App.java", 1, 5L),
                        new CoverageLineHit("src/App.java", 2, 0L),
                        new CoverageLineHit("src/App.java", 3, 3L),
                        new CoverageLineHit("src/App.java", 4, 0L),
                        new CoverageLineHit("src/App.java", 5, 2L),
                        new CoverageLineHit("src/App.java", 6, 0L)
                ),
                NOW);

        GateConfiguration gate = gateWithConfig(
                "drop-gate", "coverage_drop", "line", "0.0", new BigDecimal("3.0"), true,
                Map.of("base_percentage", "90.0", "debt", Map.of("mode", "adjust_metric")),
                "active");

        // 1. No debt -> drop 5% > 3% -> fails
        RepositoryContext contextNoDebt = new RepositoryContext("ctx-1", List.of(), List.of(), Map.of());
        List<GateEvaluation> evaluations = evaluator.evaluate(report, List.of(gate), contextNoDebt, null, NOW);
        assertEquals("failed", evaluations.get(0).status());

        // 2. Active debt covering src/App.java line 2, 4, 6 -> adjustedHeadPercentage = 100%
        // Adjusted Drop = 90% - 100% = -10% <= 3% -> passed
        UUID debtId = UUID.randomUUID();
        DebtItem debt = new DebtItem(debtId, "src/App.java", 1, 10, "active", NOW.plusSeconds(3600));
        RepositoryContext contextWithDebt = new RepositoryContext("ctx-2", List.of(), List.of(debt), Map.of());
        evaluations = evaluator.evaluate(report, List.of(gate), contextWithDebt, null, NOW);
        assertEquals("passed", evaluations.get(0).status());

        // 3. Expired debt -> fails
        DebtItem expiredDebt = new DebtItem(debtId, "src/App.java", 1, 10, "active", NOW.minusSeconds(3600));
        RepositoryContext contextExpired = new RepositoryContext("ctx-3", List.of(), List.of(expiredDebt), Map.of());
        evaluations = evaluator.evaluate(report, List.of(gate), contextExpired, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
    }

    @Test
    void evaluatesComponentCoverageGate() {
        GateEvaluator evaluator = new GateEvaluator();

        ComponentRollup rollup = new ComponentRollup(
                "comp-1",
                new CoverageMetric(8, 10),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(8, 10));

        RepositoryContext context = new RepositoryContext(
                "ctx-1",
                List.of(),
                List.of(),
                Map.of("comp-1", rollup));

        GateConfiguration gate = gateWithConfig(
                "comp-line", "component_coverage", "line", "85.0", null, true,
                Map.of("scope", Map.of("component_id", "comp-1")),
                "active");

        // 1. No debt -> 80% < 85% -> fails
        List<GateEvaluation> evaluations = evaluator.evaluate(report(), List.of(gate), context, null, NOW);
        assertEquals("failed", evaluations.get(0).status());
        assertEquals(BigDecimal.valueOf(80).setScale(4, RoundingMode.HALF_UP), evaluations.get(0).actual());

        // 2. Component rollup not found -> warning
        GateConfiguration gateMissing = gateWithConfig(
                "comp-line-missing", "component_coverage", "line", "85.0", null, true,
                Map.of("scope", Map.of("component_id", "comp-missing")),
                "active");
        evaluations = evaluator.evaluate(report(), List.of(gateMissing), context, null, NOW);
        assertEquals("warning", evaluations.get(0).status());
        assertEquals("component_rollup_not_found", evaluations.get(0).details().get("reason"));
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
