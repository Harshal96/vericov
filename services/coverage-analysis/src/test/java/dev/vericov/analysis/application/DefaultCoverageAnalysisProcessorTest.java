package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
import dev.vericov.analysis.application.port.NormalizedCoverageLocation;
import dev.vericov.analysis.application.port.NormalizedCoverageStore;
import dev.vericov.analysis.application.port.TestRunRepository;
import dev.vericov.analysis.coverage.CloverCoverageParser;
import dev.vericov.analysis.coverage.CoberturaCoverageParser;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageParserRegistry;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.coverage.GcovCoverageParser;
import dev.vericov.analysis.coverage.GoCoverProfileParser;
import dev.vericov.analysis.coverage.JacocoCoverageParser;
import dev.vericov.analysis.coverage.LcovCoverageParser;
import dev.vericov.analysis.coverage.SecureXmlCoverageDocumentReader;
import dev.vericov.analysis.domain.UploadReceivedEvent;
import dev.vericov.analysis.gates.GateConfiguration;
import dev.vericov.analysis.gates.GateEvaluation;
import dev.vericov.analysis.testresults.JUnitTestResultParser;
import dev.vericov.analysis.testresults.SecureXmlTestResultDocumentReader;
import dev.vericov.analysis.testresults.TestResultParserRegistry;
import dev.vericov.analysis.testresults.TestRun;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCoverageAnalysisProcessorTest {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID JOB_ID = UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1");
    private static final UUID TEST_ARTIFACT_ID = UUID.fromString("52d0e554-4ce9-418c-96af-2c1c4cf17e3c");

    @Test
    void downloadsLcovArtifactsAndPersistsMergedCoverageReport() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                "integration.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/integration.lcov",
                                "sha-2"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/App.java
                        DA:1,1
                        DA:2,0
                        BRDA:1,0,0,1
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8),
                "coverage-raw/tenant/upload/coverage/integration.lcov", """
                        TN:
                        SF:src/App.java
                        DA:2,7
                        DA:3,1
                        BRDA:2,0,0,0
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        FakeNormalizedCoverageStore normalizedCoverage = new FakeNormalizedCoverageStore();
        FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository(List.of(new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                REPOSITORY_ID,
                "line-minimum",
                "project_coverage",
                "line",
                new BigDecimal("90.0"),
                null,
                true,
                Map.of(),
                "active")));
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                gates,
                normalizedCoverage,
                lcovParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        CoverageReport report = reports.savedReport;
        assertEquals(UPLOAD_ID, report.uploadId());
        assertEquals(TENANT_ID, report.tenantId());
        assertEquals(REPOSITORY_ID, report.repositoryId());
        assertEquals("abc123", report.commitSha());
        assertEquals("main", report.branchName());
        assertEquals(42, report.pullRequestNumber());
        assertEquals("coverage-normalized", report.normalizedStorageBucket());
        assertEquals(
                TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz",
                report.normalizedStoragePath());
        assertEquals(3, report.line().total());
        assertEquals(3, report.line().covered());
        assertEquals(2, report.branch().total());
        assertEquals(1, report.branch().covered());
        assertEquals(1, report.files().size());
        assertEquals("src/App.java", report.files().getFirst().filePath());
        assertEquals(List.of(
                        new CoverageLineHit("src/App.java", 1, 1L),
                        new CoverageLineHit("src/App.java", 2, 7L),
                        new CoverageLineHit("src/App.java", 3, 1L)),
                report.lineHits());
        assertEquals(NOW, report.generatedAt());
        assertEquals(1, reports.savedEvaluations.size());
        GateEvaluation evaluation = reports.savedEvaluations.getFirst();
        assertEquals("line-minimum", evaluation.gateName());
        assertEquals("passed", evaluation.status());
        assertEquals(new BigDecimal("100.0000"), evaluation.actual());
        assertEquals(1, normalizedCoverage.storedReports.size());
        assertEquals(3, normalizedCoverage.storedReports.getFirst().line().total());
    }

    @Test
    void failsWhenUploadHasNoAnalyzableArtifacts() {
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                new FakeInputRepository(new CoverageAnalysisInput(
                        UPLOAD_ID,
                        TENANT_ID,
                        REPOSITORY_ID,
                        "abc123",
                        "main",
                        null,
                        List.of())),
                new FakeContentStore(Map.of()),
                new FakeReportRepository(),
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

        assertEquals("No analyzable artifacts found for upload " + UPLOAD_ID, exception.getMessage());
    }

    @Test
    void resolvesRepositoryContextOntoFileSummariesAndBuildsComponentRollupsForGates() {
        UUID componentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(new CoverageInputArtifact(
                        "unit.lcov",
                        "coverage",
                        "lcov",
                        "coverage-raw",
                        "tenant/upload/coverage/unit.lcov",
                        "sha-1"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/payments/App.java
                        DA:1,1
                        DA:2,0
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository(List.of(new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                REPOSITORY_ID,
                "payments-line-minimum",
                "component_coverage",
                "line",
                new BigDecimal("75.0"),
                null,
                true,
                Map.of("scope", Map.of("component_id", componentId.toString())),
                "active")));
        dev.vericov.analysis.gates.RepositoryContext repositoryContext = new dev.vericov.analysis.gates.RepositoryContext(
                "ctx-components-1",
                List.of(),
                List.of(),
                Map.of(),
                List.of(new dev.vericov.analysis.gates.RepositoryComponentContext(
                        componentId,
                        "payments",
                        List.of("src/payments/**"),
                        List.of("@acme/payments"),
                        "high",
                        Map.of())),
                List.of(),
                List.of(new dev.vericov.analysis.gates.RepositoryPackageNodeContext(
                        componentId,
                        "payments-service",
                        "src/payments",
                        "src/payments/package.json",
                        "npm",
                        Map.of())));
        dev.vericov.analysis.application.port.RepositoryContextRepository contextRepo =
                (tenantId, repositoryId, commitSha, branch, pr) -> repositoryContext;

        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                gates,
                contextRepo,
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                PrDiffCoverageProcessor.noop(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        CoverageReport report = reports.savedReport;
        assertEquals(componentId, report.files().getFirst().componentId());
        assertEquals("payments-service", report.files().getFirst().packageName());
        assertEquals(List.of("@acme/payments"), report.files().getFirst().owners());
        assertEquals(1, report.componentRollups().size());
        assertEquals(componentId, report.componentRollups().getFirst().componentId());
        assertEquals("@acme/payments", report.componentRollups().getFirst().owner());
        assertEquals(1, report.componentRollups().getFirst().line().covered());
        assertEquals(2, report.componentRollups().getFirst().line().total());
        assertEquals(1, report.gapFindings().size());
        assertEquals("uncovered_executable_line", report.gapFindings().getFirst().reasonCode());
        assertEquals("medium", report.gapFindings().getFirst().riskLevel());
        assertEquals(1, report.componentRollups().getFirst().gapCount());
        assertEquals(new BigDecimal("40.0"), report.componentRollups().getFirst().riskScoreTotal());
        assertEquals("medium", report.componentRollups().getFirst().highestActiveRiskLevel());
        assertEquals("failed", reports.savedEvaluations.getFirst().status());
        assertEquals(new BigDecimal("50.0000"), reports.savedEvaluations.getFirst().actual());
    }

    @Test
    void downloadsMixedCoverageArtifactsAndPersistsMergedCoverageReport() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                "jacoco.xml",
                                "coverage",
                                "jacoco",
                                "coverage-raw",
                                "tenant/upload/coverage/jacoco.xml",
                                "sha-2"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/App.java
                        DA:1,1
                        DA:2,0
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8),
                "coverage-raw/tenant/upload/coverage/jacoco.xml", """
                        <report name="unit">
                          <package name="src">
                            <class name="src/App" sourcefilename="App.java">
                              <method name="run" desc="()V" line="2">
                                <counter type="METHOD" missed="0" covered="1" />
                              </method>
                            </class>
                            <sourcefile name="App.java">
                              <line nr="2" mi="0" ci="1" mb="0" cb="0" />
                              <line nr="3" mi="0" ci="1" mb="0" cb="0" />
                            </sourcefile>
                          </package>
                        </report>
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                new FakeNormalizedCoverageStore(),
                fullParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        CoverageReport report = reports.savedReport;
        assertEquals(3, report.line().total());
        assertEquals(3, report.line().covered());
        assertEquals(1, report.function().total());
        assertEquals(1, report.function().covered());
        assertEquals(3, report.statement().total());
        assertEquals(3, report.statement().covered());
    }

    @Test
    void parsesJUnitArtifactsAndPersistsTestRunsWithoutCoverage() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(new CoverageInputArtifact(
                        TEST_ARTIFACT_ID,
                        "junit.xml",
                        "test_results",
                        "junit",
                        "test-results-raw",
                        "tenant/upload/test-results/junit.xml",
                        "sha-1"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "test-results-raw/tenant/upload/test-results/junit.xml", """
                        <testsuite name="unit" tests="3" failures="1" errors="0" skipped="1" time="1.5" />
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        FakeNormalizedCoverageStore normalizedCoverage = new FakeNormalizedCoverageStore();
        FakeTestRunRepository testRuns = new FakeTestRunRepository();
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                new FakeGateConfigurationRepository(List.of()),
                normalizedCoverage,
                lcovParserRegistry(),
                testResultParserRegistry(),
                testRuns,
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        assertNull(reports.savedReport);
        assertEquals(0, normalizedCoverage.storedReports.size());
        assertEquals(1, testRuns.savedRuns.size());
        TestRun run = testRuns.savedRuns.getFirst();
        assertEquals(TEST_ARTIFACT_ID, run.uploadArtifactId());
        assertEquals("unit", run.suiteName());
        assertEquals("failed", run.status());
        assertEquals(3, run.totalCount());
        assertEquals(1, run.passedCount());
        assertEquals(1, run.failedCount());
        assertEquals(0, run.errorCount());
        assertEquals(1, run.skippedCount());
        assertEquals(1500L, run.durationMs());
        assertEquals(NOW, run.createdAt());
    }

    @Test
    void parsesCoverageAndJUnitArtifactsInTheSameUpload() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                TEST_ARTIFACT_ID,
                                "junit.xml",
                                "test_results",
                                "junit",
                                "test-results-raw",
                                "tenant/upload/test-results/junit.xml",
                                "sha-2"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/App.java
                        DA:1,1
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8),
                "test-results-raw/tenant/upload/test-results/junit.xml", """
                        <testsuite name="unit" tests="1" failures="0" errors="0" skipped="0" />
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        FakeTestRunRepository testRuns = new FakeTestRunRepository();
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                new FakeGateConfigurationRepository(List.of()),
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                testResultParserRegistry(),
                testRuns,
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        assertEquals(1, reports.savedReport.line().covered());
        assertEquals(1, testRuns.savedRuns.size());
        assertEquals("passed", testRuns.savedRuns.getFirst().status());
    }

    @Test
    void allCoverageFilesExcludedPersistsEmptyReportAndTestRuns() {
        CoverageAnalysisInput input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "github",
                "abc123",
                "main",
                42,
                List.of("generated/**"),
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"),
                        new CoverageInputArtifact(
                                "integration.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/integration.lcov",
                                "sha-2"),
                        new CoverageInputArtifact(
                                TEST_ARTIFACT_ID,
                                "junit.xml",
                                "test_results",
                                "junit",
                                "test-results-raw",
                                "tenant/upload/test-results/junit.xml",
                                "sha-3")));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:generated/One.java
                        DA:1,1
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8),
                "coverage-raw/tenant/upload/coverage/integration.lcov", """
                        TN:
                        SF:generated/Two.java
                        DA:1,0
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8),
                "test-results-raw/tenant/upload/test-results/junit.xml", """
                        <testsuite name="unit" tests="1" failures="0" errors="0" skipped="0" />
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        FakeNormalizedCoverageStore normalizedCoverage = new FakeNormalizedCoverageStore();
        FakeTestRunRepository testRuns = new FakeTestRunRepository();
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                new FakeInputRepository(input),
                content,
                reports,
                new FakeGateConfigurationRepository(List.of()),
                normalizedCoverage,
                lcovParserRegistry(),
                testResultParserRegistry(),
                testRuns,
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        CoverageReport report = reports.savedReport;
        assertEquals(0, report.line().covered());
        assertEquals(0, report.line().total());
        assertEquals(List.of(), report.files());
        assertEquals(List.of(), report.lineHits());
        assertEquals(List.of(), report.gapFindings());
        assertEquals(List.of(), report.componentRollups());
        assertEquals(1, normalizedCoverage.storedReports.size());
        assertEquals(List.of(), normalizedCoverage.storedReports.getFirst().files());
        assertEquals(List.of(), normalizedCoverage.storedReports.getFirst().lineHits());
        assertEquals(1, testRuns.savedRuns.size());
    }

    @Test
    void failsWhenTestResultArtifactFormatIsUnsupported() {
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                new FakeInputRepository(new CoverageAnalysisInput(
                        UPLOAD_ID,
                        TENANT_ID,
                        REPOSITORY_ID,
                        "abc123",
                        "main",
                        null,
                        List.of(new CoverageInputArtifact(
                                TEST_ARTIFACT_ID,
                                "results.xml",
                                "test_results",
                                "xunit",
                                "test-results-raw",
                                "tenant/upload/test-results/results.xml",
                                "sha-1")))),
                new FakeContentStore(Map.of("test-results-raw/tenant/upload/test-results/results.xml", new byte[0])),
                new FakeReportRepository(),
                new FakeGateConfigurationRepository(List.of()),
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                testResultParserRegistry(),
                new FakeTestRunRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

        assertEquals("Unsupported test result artifact format: xunit for results.xml", exception.getMessage());
    }

    @Test
    void failsWithoutSavingReportWhenNormalizedCoverageStorageFails() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(new CoverageInputArtifact(
                        "unit.lcov",
                        "coverage",
                        "lcov",
                        "coverage-raw",
                        "tenant/upload/coverage/unit.lcov",
                        "sha-1"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/App.java
                        DA:1,1
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                new FakeGateConfigurationRepository(List.of()),
                new FakeNormalizedCoverageStore(new IllegalStateException("normalized upload failed")),
                lcovParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

        assertEquals("normalized upload failed", exception.getMessage());
        assertNull(reports.savedReport);
    }

    @Test
    void processesUploadWithDebtAwareGateEvaluation() {
        FakeInputRepository inputs = new FakeInputRepository(new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of(
                        new CoverageInputArtifact(
                                "unit.lcov",
                                "coverage",
                                "lcov",
                                "coverage-raw",
                                "tenant/upload/coverage/unit.lcov",
                                "sha-1"))));
        FakeContentStore content = new FakeContentStore(Map.of(
                "coverage-raw/tenant/upload/coverage/unit.lcov", """
                        TN:
                        SF:src/App.java
                        DA:1,1
                        DA:2,0
                        DA:3,1
                        DA:4,0
                        end_of_record
                        """.getBytes(StandardCharsets.UTF_8)));
        FakeReportRepository reports = new FakeReportRepository();

        // Gate requires 90% coverage
        FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository(List.of(new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                REPOSITORY_ID,
                "line-minimum",
                "project_coverage",
                "line",
                new BigDecimal("90.0"),
                null,
                true,
                Map.of("debt", Map.of("mode", "adjust_metric")),
                "active")));

        // Active debt on line 2 (uncovered), expired debt on line 4 (uncovered)
        UUID activeDebtId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID expiredDebtId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        dev.vericov.analysis.gates.DebtItem activeDebt = new dev.vericov.analysis.gates.DebtItem(
                activeDebtId,
                "src/App.java",
                2,
                2,
                "active",
                NOW.plusSeconds(3600));
        dev.vericov.analysis.gates.DebtItem expiredDebt = new dev.vericov.analysis.gates.DebtItem(
                expiredDebtId,
                "src/App.java",
                4,
                4,
                "active",
                NOW.minusSeconds(3600));

        dev.vericov.analysis.gates.RepositoryContext repositoryContext = new dev.vericov.analysis.gates.RepositoryContext(
                "ctx-version-123",
                List.of(),
                List.of(activeDebt, expiredDebt),
                Map.of());

        dev.vericov.analysis.application.port.RepositoryContextRepository contextRepo =
                (tenantId, repositoryId, commitSha, branch, pr) -> {
                    assertEquals(TENANT_ID, tenantId);
                    assertEquals(REPOSITORY_ID, repositoryId);
                    assertEquals("abc123", commitSha);
                    assertEquals("main", branch);
                    assertEquals(42, pr);
                    return repositoryContext;
                };

        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                inputs,
                content,
                reports,
                gates,
                contextRepo,
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                PrDiffCoverageProcessor.noop(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        processor.process(event());

        // Report has total lines: 4, covered: 2 (lines 1, 3 are covered, 2, 4 are uncovered)
        // Raw coverage is 50%
        // Under adjust_metric, the active debt on line 2 reduces the total to 3.
        // Effective coverage becomes 2/3 = 66.67%
        // But since there is an expired debt on line 4, the gate should fail with expired_debt_reappeared!
        CoverageReport report = reports.savedReport;
        assertEquals(4, report.line().total());
        assertEquals(2, report.line().covered());

        assertEquals(1, reports.savedEvaluations.size());
        GateEvaluation evaluation = reports.savedEvaluations.getFirst();
        assertEquals("line-minimum", evaluation.gateName());
        assertEquals("failed", evaluation.status()); // failed due to expired debt
        assertEquals(new BigDecimal("66.6667"), evaluation.actual()); // effective actual metric

        Map<?, ?> details = evaluation.details();
        assertEquals(2, details.get("schema_version"));

        Map<?, ?> raw = (Map<?, ?>) details.get("raw");
        assertEquals(2, raw.get("covered"));
        assertEquals(4, raw.get("total"));
        assertEquals("failed", raw.get("status"));

        Map<?, ?> debt = (Map<?, ?>) details.get("debt");
        assertEquals("adjust_metric", debt.get("mode"));
        assertEquals(List.of(activeDebtId.toString()), debt.get("suppressed_debt_item_ids"));
        assertEquals(List.of(expiredDebtId.toString()), debt.get("expired_debt_item_ids"));

        Map<?, ?> effective = (Map<?, ?>) details.get("effective");
        assertEquals("failed", effective.get("status"));
        assertEquals("expired_debt_reappeared", effective.get("reason"));
    }

    @Test
    void failsWhenCoverageArtifactFormatIsUnsupported() {
        DefaultCoverageAnalysisProcessor processor = new DefaultCoverageAnalysisProcessor(
                new FakeInputRepository(new CoverageAnalysisInput(
                        UPLOAD_ID,
                        TENANT_ID,
                        REPOSITORY_ID,
                        "abc123",
                        "main",
                        null,
                        List.of(new CoverageInputArtifact(
                                "coverage.xml",
                                "coverage",
                                "jacoco",
                                "coverage-raw",
                                "tenant/upload/coverage/coverage.xml",
                                "sha-1")))),
                new FakeContentStore(Map.of("coverage-raw/tenant/upload/coverage/coverage.xml", new byte[0])),
                new FakeReportRepository(),
                new FakeNormalizedCoverageStore(),
                lcovParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

        assertEquals("Unsupported coverage artifact format: jacoco for coverage.xml", exception.getMessage());
    }

    private static UploadReceivedEvent event() {
        return new UploadReceivedEvent(
                1,
                "upload.received",
                UPLOAD_ID,
                JOB_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123");
    }

    private static CoverageParserRegistry lcovParserRegistry() {
        return new CoverageParserRegistry(List.of(new LcovCoverageParser()));
    }

    private static CoverageParserRegistry fullParserRegistry() {
        SecureXmlCoverageDocumentReader xmlReader = new SecureXmlCoverageDocumentReader();
        return new CoverageParserRegistry(List.of(
                new LcovCoverageParser(),
                new CoberturaCoverageParser(xmlReader),
                new JacocoCoverageParser(xmlReader),
                new CloverCoverageParser(xmlReader),
                new GoCoverProfileParser(),
                new GcovCoverageParser()));
    }

    private static TestResultParserRegistry testResultParserRegistry() {
        return new TestResultParserRegistry(List.of(new JUnitTestResultParser(new SecureXmlTestResultDocumentReader())));
    }

    private record FakeInputRepository(CoverageAnalysisInput input) implements CoverageAnalysisInputRepository {
        @Override
        public CoverageAnalysisInput load(UUID uploadId) {
            return input;
        }
    }

    private record FakeContentStore(Map<String, byte[]> contentByLocation) implements ArtifactContentStore {
        @Override
        public byte[] read(String bucket, String storagePath) {
            return contentByLocation.get(bucket + "/" + storagePath);
        }
    }

    private static final class FakeReportRepository implements CoverageReportRepository {
        private CoverageReport savedReport;
        private List<GateEvaluation> savedEvaluations = List.of();

        @Override
        public void save(CoverageReport report) {
            savedReport = report;
        }

        @Override
        public void save(CoverageReport report, List<GateEvaluation> evaluations) {
            savedReport = report;
            savedEvaluations = List.copyOf(evaluations);
        }

        @Override
        public Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha) {
            return Optional.empty();
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId) {
            return List.of();
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath) {
            return List.of();
        }
    }

    private record FakeGateConfigurationRepository(List<GateConfiguration> gates)
            implements GateConfigurationRepository {
        @Override
        public List<GateConfiguration> listActiveForRepository(UUID tenantId, UUID repositoryId) {
            assertEquals(TENANT_ID, tenantId);
            assertEquals(REPOSITORY_ID, repositoryId);
            return gates;
        }
    }

    private static final class FakeNormalizedCoverageStore implements NormalizedCoverageStore {
        private final List<CoverageReport> storedReports = new java.util.ArrayList<>();
        private final RuntimeException failure;

        private FakeNormalizedCoverageStore() {
            this(null);
        }

        private FakeNormalizedCoverageStore(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public NormalizedCoverageLocation store(CoverageReport report) {
            if (failure != null) {
                throw failure;
            }
            storedReports.add(report);
            return new NormalizedCoverageLocation(
                    "coverage-normalized",
                    report.tenantId() + "/" + report.uploadId() + "/coverage-normalized/coverage-map.json.gz");
        }
    }

    private static final class FakeTestRunRepository implements TestRunRepository {
        private List<TestRun> savedRuns = List.of();

        @Override
        public void save(CoverageAnalysisInput input, List<TestRun> runs, Instant completedAt) {
            assertEquals(UPLOAD_ID, input.uploadId());
            assertEquals(NOW, completedAt);
            savedRuns = List.copyOf(runs);
        }
    }
}
