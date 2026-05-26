package dev.vericov.analysis.gaps;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import dev.vericov.analysis.gates.DebtItem;
import dev.vericov.analysis.gates.RepositoryComponentContext;
import dev.vericov.analysis.gates.RepositoryContext;
import dev.vericov.analysis.gates.RepositoryPackageNodeContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageGapExtractorTest {
    private static final UUID REPORT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID UPLOAD_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TENANT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REPOSITORY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID COMPONENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-05-25T12:00:00Z");

    @Test
    void scoresChangedUncoveredLinesWithExplainableFactors() {
        CoverageReport report = report(
                "services/payments/src/App.java",
                new CoverageMetric(0, 1),
                List.of(new CoverageLineHit("services/payments/src/App.java", 42, 0)),
                List.of("@acme/payments"));
        RepositoryContext context = new RepositoryContext(
                "ctx-risk-1",
                List.of(),
                List.of(),
                Map.of(),
                List.of(new RepositoryComponentContext(
                        COMPONENT_ID,
                        "payments",
                        List.of("services/payments/**"),
                        List.of("@acme/payments"),
                        "critical",
                        Map.of())),
                List.of(),
                List.of(new RepositoryPackageNodeContext(
                        COMPONENT_ID,
                        "payments-service",
                        "services/payments",
                        "services/payments/pom.xml",
                        "maven",
                        Map.of("dependent_count", 5))),
                Map.of("path_overrides", List.of(Map.of(
                        "pattern", "services/payments/**",
                        "score_boost", 15))));
        DiffCoverageReport diff = new DiffCoverageReport(
                "base123",
                "head456",
                0,
                1,
                1,
                0,
                List.of(new DiffCoverageFile(
                        "services/payments/src/App.java",
                        null,
                        "modified",
                        0,
                        1,
                        1,
                        0,
                        List.of(new DiffCoverageLine(
                                "services/payments/src/App.java",
                                null,
                                null,
                                42,
                                DiffLineType.ADDED,
                                true,
                                null,
                                0L,
                                true,
                                false)))));

        List<CoverageGapFinding> findings = new CoverageGapExtractor().extract(report, context, diff, NOW);

        assertEquals(2, findings.size());
        CoverageGapFinding changedLine = findings.getFirst();
        assertEquals("new_uncovered_changed_line", changedLine.reasonCode());
        assertEquals(new BigDecimal("89.0"), changedLine.riskScore());
        assertEquals("critical", changedLine.riskLevel());
        assertEquals("add_test", changedLine.nextAction());
        assertEquals("active", changedLine.status());
        assertEquals(COMPONENT_ID, changedLine.componentId());
        assertEquals(List.of("@acme/payments"), changedLine.owners());
        assertTrue(changedLine.explanation().contains("Added executable line 42 is uncovered"));

        Map<?, ?> score = (Map<?, ?>) changedLine.evidence().get("score");
        assertEquals(1, score.get("schema_version"));
        assertEquals("critical", score.get("level"));
        assertEquals(new BigDecimal("89.0"), score.get("total"));
        List<?> factors = (List<?>) score.get("factors");
        assertTrue(factors.stream().anyMatch(factor -> ((Map<?, ?>) factor).get("name").equals("policy_override")));
    }

    @Test
    void suppressesMatchingActiveDebtAndLowersRisk() {
        UUID debtId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        CoverageReport report = report(
                "src/App.java",
                new CoverageMetric(0, 1),
                List.of(new CoverageLineHit("src/App.java", 10, 0)),
                List.of("@acme/app"));
        RepositoryContext context = new RepositoryContext(
                "ctx-debt-1",
                List.of(),
                List.of(new DebtItem(debtId, "src/App.java", 1, 20, "active", NOW.plusSeconds(3600))),
                Map.of());

        List<CoverageGapFinding> findings = new CoverageGapExtractor().extract(report, context, null, NOW);

        assertEquals(2, findings.size());
        CoverageGapFinding lineFinding = findings.stream()
                .filter(finding -> "uncovered_executable_line".equals(finding.reasonCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("debt_suppressed", lineFinding.status());
        assertEquals("low", lineFinding.riskLevel());
        assertEquals(List.of(debtId.toString()), lineFinding.evidence().get("debt_item_ids"));

        Map<?, ?> score = (Map<?, ?>) lineFinding.evidence().get("score");
        List<?> factors = (List<?>) score.get("factors");
        assertTrue(factors.stream().anyMatch(factor -> {
            Map<?, ?> contribution = (Map<?, ?>) factor;
            return contribution.get("name").equals("debt_state")
                    && contribution.get("value").equals(new BigDecimal("-20.0"));
        }));
    }

    @Test
    void groupsAdjacentUncoveredReportLinesIntoRangeFindings() {
        CoverageReport report = report(
                "src/App.java",
                new CoverageMetric(1, 4),
                List.of(
                        new CoverageLineHit("src/App.java", 10, 0),
                        new CoverageLineHit("src/App.java", 11, 0),
                        new CoverageLineHit("src/App.java", 13, 0),
                        new CoverageLineHit("src/App.java", 20, 1)),
                List.of("@acme/app"));
        RepositoryContext context = new RepositoryContext("ctx-ranges", List.of(), List.of(), Map.of());

        List<CoverageGapFinding> findings = new CoverageGapExtractor().extract(report, context, null, NOW);

        List<CoverageGapFinding> uncovered = findings.stream()
                .filter(finding -> "uncovered_executable_line".equals(finding.reasonCode()))
                .toList();
        assertEquals(2, uncovered.size());
        CoverageGapFinding range = uncovered.getFirst();
        assertEquals("range", range.targetType());
        assertEquals(10, range.lineStart());
        assertEquals(11, range.lineEnd());
        assertEquals("Lines 10-11 are uncovered in the coverage report.", range.explanation());

        CoverageGapFinding single = uncovered.get(1);
        assertEquals("line", single.targetType());
        assertEquals(13, single.lineStart());
        assertEquals(13, single.lineEnd());
    }

    @Test
    void classifiesBaseMissingPathMismatchesMissingPathsAndGeneratedCandidates() {
        CoverageReport report = report(
                "src/main/java/com/acme/App.java",
                new CoverageMetric(1, 1),
                List.of(new CoverageLineHit("src/main/java/com/acme/App.java", 42, 1)),
                List.of("@acme/app"));
        RepositoryContext context = new RepositoryContext(
                "ctx-paths",
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of("generated_path_patterns", List.of("build/generated/**")));
        DiffCoverageReport diff = new DiffCoverageReport(
                "base123",
                "head456",
                "base_coverage_missing",
                0,
                0,
                0,
                0,
                List.of(
                        new DiffCoverageFile(
                                "App.java",
                                null,
                                "modified",
                                0,
                                0,
                                0,
                                0,
                                List.of(new DiffCoverageLine(
                                        "App.java",
                                        null,
                                        null,
                                        42,
                                        DiffLineType.ADDED,
                                        false,
                                        null,
                                        null,
                                        false,
                                        false))),
                        new DiffCoverageFile(
                                "lib/Missing.java",
                                null,
                                "modified",
                                0,
                                0,
                                0,
                                0,
                                List.of(new DiffCoverageLine(
                                        "lib/Missing.java",
                                        null,
                                        null,
                                        7,
                                        DiffLineType.ADDED,
                                        false,
                                        null,
                                        null,
                                        false,
                                        false))),
                        new DiffCoverageFile(
                                "build/generated/Generated.java",
                                null,
                                "added",
                                0,
                                0,
                                0,
                                0,
                                List.of(new DiffCoverageLine(
                                        "build/generated/Generated.java",
                                        null,
                                        null,
                                        3,
                                        DiffLineType.ADDED,
                                        false,
                                        null,
                                        null,
                                        false,
                                        false)))));

        List<CoverageGapFinding> findings = new CoverageGapExtractor().extract(report, context, diff, NOW);

        assertTrue(findings.stream().anyMatch(finding ->
                "base_coverage_missing".equals(finding.reasonCode())
                        && "run_source_explain".equals(finding.nextAction())
                        && finding.explanation().contains("Base coverage is unavailable")));
        assertTrue(findings.stream().anyMatch(finding ->
                "possible_path_mismatch".equals(finding.reasonCode())
                        && "App.java".equals(finding.filePath())
                        && "inspect_instrumentation".equals(finding.nextAction())
                        && "medium".equals(finding.confidence())));
        assertTrue(findings.stream().anyMatch(finding ->
                "path_not_in_report".equals(finding.reasonCode())
                        && "lib/Missing.java".equals(finding.filePath())
                        && "inspect_instrumentation".equals(finding.nextAction())));
        assertTrue(findings.stream().anyMatch(finding ->
                "generated_or_ignored_candidate".equals(finding.reasonCode())
                        && "build/generated/Generated.java".equals(finding.filePath())
                        && "mark_generated".equals(finding.nextAction())
                        && "low".equals(finding.confidence())));
    }

    private static CoverageReport report(
            String filePath,
            CoverageMetric lineMetric,
            List<CoverageLineHit> lineHits,
            List<String> owners) {
        return new CoverageReport(
                REPORT_ID,
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "head456",
                "main",
                42,
                lineMetric,
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                lineMetric,
                List.of(new CoverageFileSummary(
                        filePath,
                        lineMetric,
                        new CoverageMetric(0, 0),
                        new CoverageMetric(0, 0),
                        lineMetric,
                        COMPONENT_ID,
                        "app",
                        owners)),
                lineHits,
                NOW);
    }
}
