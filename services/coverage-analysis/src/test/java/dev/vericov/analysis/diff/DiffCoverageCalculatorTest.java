package dev.vericov.analysis.diff;

import dev.vericov.analysis.coverage.CoverageLineHit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiffCoverageCalculatorTest {
    @Test
    void calculatesPatchCoverageFromChangedExecutableHeadLines() {
        PullRequestDiff diff = new PullRequestDiff("base123", "head456", List.of(new PullRequestDiffFile(
                "src/App.java",
                null,
                "modified",
                List.of(
                        new PullRequestDiffLine(null, 10, DiffLineType.ADDED),
                        new PullRequestDiffLine(null, 11, DiffLineType.ADDED),
                        new PullRequestDiffLine(20, 20, DiffLineType.CONTEXT)))));
        List<CoverageLineHit> headHits = List.of(
                new CoverageLineHit("src/App.java", 10, 4L),
                new CoverageLineHit("src/App.java", 11, 0L),
                new CoverageLineHit("src/App.java", 20, 0L));

        DiffCoverageReport report = new DiffCoverageCalculator().calculate(diff, headHits, List.of());

        assertEquals(2, report.patchLineTotal());
        assertEquals(1, report.patchLineCovered());
        assertEquals(new BigDecimal("50.00"), report.patchLinePercentage());
        assertEquals(List.of(11), report.files().getFirst().newlyMissedLines().stream()
                .map(DiffCoverageLine::headLineNumber)
                .toList());
    }

    @Test
    void identifiesLostCoverageOnMappedLines() {
        PullRequestDiff diff = new PullRequestDiff("base123", "head456", List.of(new PullRequestDiffFile(
                "src/App.java",
                null,
                "modified",
                List.of(new PullRequestDiffLine(30, 31, DiffLineType.CONTEXT)))));
        List<CoverageLineHit> baseHits = List.of(new CoverageLineHit("src/App.java", 30, 7L));
        List<CoverageLineHit> headHits = List.of(new CoverageLineHit("src/App.java", 31, 0L));

        DiffCoverageReport report = new DiffCoverageCalculator().calculate(diff, headHits, baseHits);

        assertEquals(1, report.lostCoverageLineCount());
        DiffCoverageLine lost = report.files().getFirst().lostCoverageLines().getFirst();
        assertEquals(30, lost.baseLineNumber());
        assertEquals(31, lost.headLineNumber());
        assertEquals(7L, lost.baseHits());
        assertEquals(0L, lost.headHits());
    }
}
