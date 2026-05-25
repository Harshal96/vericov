package dev.vericov.analysis.diff;

import java.util.List;
import java.util.Objects;

public record DiffCoverageFile(
        String filePath,
        String oldFilePath,
        String changeStatus,
        int patchLineCovered,
        int patchLineTotal,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        List<DiffCoverageLine> lines) {

    public DiffCoverageFile {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(changeStatus, "changeStatus");
        oldFilePath = oldFilePath == null || oldFilePath.isBlank() ? null : oldFilePath;
        lines = List.copyOf(lines == null ? List.of() : lines);
    }

    public List<DiffCoverageLine> newlyMissedLines() {
        return lines.stream().filter(DiffCoverageLine::newlyMissed).toList();
    }

    public List<DiffCoverageLine> lostCoverageLines() {
        return lines.stream().filter(DiffCoverageLine::lostCoverage).toList();
    }
}
