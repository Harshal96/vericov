package dev.vericov.analysis.diff;

import dev.vericov.analysis.coverage.CoverageLineHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DiffCoverageCalculator {
    public DiffCoverageReport calculate(
            PullRequestDiff diff,
            List<CoverageLineHit> headLineHits,
            List<CoverageLineHit> baseLineHits) {
        Objects.requireNonNull(diff, "diff");
        Map<LineKey, Long> headHits = lineHitsByKey(headLineHits);
        Map<LineKey, Long> baseHits = lineHitsByKey(baseLineHits);
        List<DiffCoverageFile> files = new ArrayList<>();
        int patchCovered = 0;
        int patchTotal = 0;
        int newlyMissed = 0;
        int lostCoverage = 0;

        for (PullRequestDiffFile file : diff.files()) {
            FileCoverageAccumulator accumulator = calculateFile(file, headHits, baseHits);
            files.add(accumulator.file());
            patchCovered += accumulator.file().patchLineCovered();
            patchTotal += accumulator.file().patchLineTotal();
            newlyMissed += accumulator.file().newlyMissedLineCount();
            lostCoverage += accumulator.file().lostCoverageLineCount();
        }

        return new DiffCoverageReport(
                diff.baseSha(),
                diff.headSha(),
                patchCovered,
                patchTotal,
                newlyMissed,
                lostCoverage,
                files);
    }

    private static FileCoverageAccumulator calculateFile(
            PullRequestDiffFile file,
            Map<LineKey, Long> headHits,
            Map<LineKey, Long> baseHits) {
        String headPath = file.filePath();
        String basePath = file.oldFilePath() == null ? file.filePath() : file.oldFilePath();
        List<DiffCoverageLine> lines = new ArrayList<>();
        int patchCovered = 0;
        int patchTotal = 0;
        int newlyMissed = 0;
        int lostCoverage = 0;

        for (PullRequestDiffLine line : file.lines()) {
            Long headLineHits = line.headLineNumber() == null
                    ? null
                    : headHits.get(new LineKey(headPath, line.headLineNumber()));
            Long baseLineHits = line.baseLineNumber() == null
                    ? null
                    : baseHits.get(new LineKey(basePath, line.baseLineNumber()));
            boolean executable = headLineHits != null;
            boolean lineNewlyMissed = line.type() == DiffLineType.ADDED && executable && headLineHits == 0;
            boolean lineLostCoverage = line.type() == DiffLineType.CONTEXT
                    && baseLineHits != null
                    && baseLineHits > 0
                    && headLineHits != null
                    && headLineHits == 0;

            if (line.type() == DiffLineType.ADDED && executable) {
                patchTotal++;
                if (headLineHits > 0) {
                    patchCovered++;
                }
            }
            if (lineNewlyMissed) {
                newlyMissed++;
            }
            if (lineLostCoverage) {
                lostCoverage++;
            }
            lines.add(new DiffCoverageLine(
                    headPath,
                    file.oldFilePath(),
                    line.baseLineNumber(),
                    line.headLineNumber(),
                    line.type(),
                    executable,
                    baseLineHits,
                    headLineHits,
                    lineNewlyMissed,
                    lineLostCoverage));
        }

        return new FileCoverageAccumulator(new DiffCoverageFile(
                headPath,
                file.oldFilePath(),
                file.changeStatus(),
                patchCovered,
                patchTotal,
                newlyMissed,
                lostCoverage,
                lines));
    }

    private static Map<LineKey, Long> lineHitsByKey(List<CoverageLineHit> lineHits) {
        Map<LineKey, Long> hitsByKey = new HashMap<>();
        for (CoverageLineHit lineHit : lineHits == null ? List.<CoverageLineHit>of() : lineHits) {
            hitsByKey.merge(new LineKey(lineHit.filePath(), lineHit.lineNumber()), lineHit.hits(), Long::sum);
        }
        return Map.copyOf(hitsByKey);
    }

    private record FileCoverageAccumulator(DiffCoverageFile file) {
    }

    private record LineKey(String filePath, int lineNumber) {
    }
}
