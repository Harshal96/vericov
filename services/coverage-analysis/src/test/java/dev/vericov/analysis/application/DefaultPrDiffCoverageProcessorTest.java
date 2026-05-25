package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.application.port.PrDiffCoverageRepository;
import dev.vericov.analysis.application.port.PullRequestDiffClient;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.diff.DiffCoverageReport;
import dev.vericov.analysis.diff.DiffLineType;
import dev.vericov.analysis.diff.PullRequestDiff;
import dev.vericov.analysis.diff.PullRequestDiffFile;
import dev.vericov.analysis.diff.PullRequestDiffLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultPrDiffCoverageProcessorTest {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID ORG_ID = UUID.fromString("8247a65f-9d3d-4d61-8cc9-1f2692c6da9e");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID REPORT_ID = UUID.fromString("29bf4d81-edf3-47d9-8f90-c24037396473");
    private static final UUID BASE_REPORT_ID = UUID.fromString("bc63c12d-e656-4816-80a1-69209ed7e9ed");

    @Test
    void fetchesExactPrDiffAndPersistsPatchCoverage() {
        FakePullRequestDiffClient diffClient = new FakePullRequestDiffClient(diff());
        FakeCoverageReportRepository reports = new FakeCoverageReportRepository(Optional.of(baseSummary()));
        reports.baseLineHits = List.of(new CoverageLineHit("src/App.java", 30, 4L));
        FakePrDiffCoverageRepository prDiffs = new FakePrDiffCoverageRepository();

        processor(diffClient, reports, prDiffs).process(input(), reportWithHits(
                new CoverageLineHit("src/App.java", 10, 1L),
                new CoverageLineHit("src/App.java", 11, 0L),
                new CoverageLineHit("src/App.java", 31, 0L)));

        assertEquals("complete", prDiffs.status);
        assertEquals("base123", prDiffs.savedReport.baseSha());
        assertEquals("head456", prDiffs.savedReport.headSha());
        assertEquals(2, prDiffs.savedReport.patchLineTotal());
        assertEquals(1, prDiffs.savedReport.patchLineCovered());
        assertEquals(1, prDiffs.savedReport.newlyMissedLineCount());
        assertEquals(1, prDiffs.savedReport.lostCoverageLineCount());
    }

    @Test
    void calculatesPatchCoverageWhenBaseCoverageIsMissing() {
        FakePrDiffCoverageRepository prDiffs = new FakePrDiffCoverageRepository();

        processor(new FakePullRequestDiffClient(diff()), new FakeCoverageReportRepository(Optional.empty()), prDiffs)
                .process(input(), reportWithHits(new CoverageLineHit("src/App.java", 10, 0L)));

        assertEquals("base_coverage_missing", prDiffs.status);
        assertEquals(1, prDiffs.savedReport.patchLineTotal());
        assertEquals(0, prDiffs.savedReport.patchLineCovered());
        assertEquals(0, prDiffs.savedReport.lostCoverageLineCount());
    }

    private static DefaultPrDiffCoverageProcessor processor(
            PullRequestDiffClient diffClient,
            CoverageReportRepository reports,
            PrDiffCoverageRepository prDiffs) {
        return new DefaultPrDiffCoverageProcessor(diffClient, reports, prDiffs);
    }

    private static CoverageAnalysisInput input() {
        return new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                ORG_ID,
                "github",
                "head456",
                "feature/diff",
                42,
                List.of());
    }

    private static PullRequestDiff diff() {
        return new PullRequestDiff("base123", "head456", List.of(new PullRequestDiffFile(
                "src/App.java",
                null,
                "modified",
                List.of(
                        new PullRequestDiffLine(null, 10, DiffLineType.ADDED),
                        new PullRequestDiffLine(null, 11, DiffLineType.ADDED),
                        new PullRequestDiffLine(30, 31, DiffLineType.CONTEXT)))));
    }

    private static CoverageReport reportWithHits(CoverageLineHit... lineHits) {
        return new CoverageReport(
                REPORT_ID,
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "head456",
                "feature/diff",
                42,
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                List.of(),
                List.of(lineHits),
                NOW);
    }

    private static CoverageReportSummary baseSummary() {
        return new CoverageReportSummary(
                BASE_REPORT_ID,
                TENANT_ID,
                REPOSITORY_ID,
                UPLOAD_ID,
                "base123",
                "main",
                null,
                NOW,
                NOW);
    }

    private record FakePullRequestDiffClient(PullRequestDiff diff) implements PullRequestDiffClient {
        @Override
        public PullRequestDiff fetch(CoverageAnalysisInput input, CoverageReport headReport) {
            return diff;
        }
    }

    private static final class FakeCoverageReportRepository implements CoverageReportRepository {
        private final Optional<CoverageReportSummary> baseSummary;
        private List<CoverageLineHit> baseLineHits = List.of();

        private FakeCoverageReportRepository(Optional<CoverageReportSummary> baseSummary) {
            this.baseSummary = baseSummary;
        }

        @Override
        public void save(CoverageReport report) {
        }

        @Override
        public Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha) {
            return baseSummary;
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId) {
            return baseLineHits;
        }

        @Override
        public List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath) {
            return baseLineHits.stream().filter(line -> line.filePath().equals(filePath)).toList();
        }
    }

    private static final class FakePrDiffCoverageRepository implements PrDiffCoverageRepository {
        private String status;
        private DiffCoverageReport savedReport;

        @Override
        public void save(
                UUID tenantId,
                UUID repositoryId,
                UUID coverageReportId,
                int pullRequestNumber,
                String providerKey,
                String status,
                DiffCoverageReport report) {
            this.status = status;
            this.savedReport = report;
        }
    }
}
