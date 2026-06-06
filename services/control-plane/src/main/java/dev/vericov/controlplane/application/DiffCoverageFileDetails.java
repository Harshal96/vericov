package dev.vericov.controlplane.application;

import java.util.List;

public record DiffCoverageFileDetails(
        String filePath,
        String oldFilePath,
        String changeStatus,
        CoverageMetricDetails patchLine,
        int newlyMissedLineCount,
        int lostCoverageLineCount,
        List<DiffCoverageLineDetails> lines) {

    public DiffCoverageFileDetails {
        lines = List.copyOf(lines == null ? List.of() : lines);
    }
}
