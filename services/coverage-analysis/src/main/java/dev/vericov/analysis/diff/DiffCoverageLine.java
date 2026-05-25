package dev.vericov.analysis.diff;

import java.util.Objects;

public record DiffCoverageLine(
        String filePath,
        String oldFilePath,
        Integer baseLineNumber,
        Integer headLineNumber,
        DiffLineType changeType,
        boolean executable,
        Long baseHits,
        Long headHits,
        boolean newlyMissed,
        boolean lostCoverage) {

    public DiffCoverageLine {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(changeType, "changeType");
        oldFilePath = oldFilePath == null || oldFilePath.isBlank() ? null : oldFilePath;
    }
}
