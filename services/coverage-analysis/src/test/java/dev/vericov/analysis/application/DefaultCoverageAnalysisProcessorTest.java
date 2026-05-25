package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.GateConfigurationRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCoverageAnalysisProcessorTest {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID JOB_ID = UUID.fromString("fb0e1e5d-55d7-4f74-9303-7a93400d53a1");

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
        FakeGateConfigurationRepository gates = new FakeGateConfigurationRepository(List.of(new GateConfiguration(
                UUID.fromString("6ca2b9dc-75f0-45e7-b28b-a76c4db133d9"),
                TENANT_ID,
                UUID.fromString("2ca9c094-7c28-4cb9-9b99-aae95cf07050"),
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
    }

    @Test
    void failsWhenUploadHasNoCoverageArtifacts() {
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
                lcovParserRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> processor.process(event()));

        assertEquals("No coverage artifacts found for upload " + UPLOAD_ID, exception.getMessage());
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
}
