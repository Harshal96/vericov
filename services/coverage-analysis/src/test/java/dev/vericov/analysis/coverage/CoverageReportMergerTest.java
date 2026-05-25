package dev.vericov.analysis.coverage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageReportMergerTest {
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");

    @Test
    void sumsLineHitsAcrossCoverageShards() {
        CoverageAnalysisInput input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "head123",
                "feature/diff",
                42,
                List.of());
        ParsedCoverage first = new ParsedCoverage(List.of(new ParsedCoverageFile(
                "src/App.java",
                Map.of(10, 1L, 11, 0L),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of())));
        ParsedCoverage second = new ParsedCoverage(List.of(new ParsedCoverageFile(
                "src/App.java",
                Map.of(10, 2L, 11, 4L),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of())));

        CoverageReport report = new CoverageReportMerger().merge(input, List.of(first, second), NOW);

        assertEquals(2, report.line().total());
        assertEquals(2, report.line().covered());
        assertEquals(List.of(
                        new CoverageLineHit("src/App.java", 10, 3L),
                        new CoverageLineHit("src/App.java", 11, 4L)),
                report.lineHits());
    }
}
