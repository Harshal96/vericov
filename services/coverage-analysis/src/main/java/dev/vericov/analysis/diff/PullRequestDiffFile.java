package dev.vericov.analysis.diff;

import java.util.List;
import java.util.Objects;

public record PullRequestDiffFile(
        String filePath,
        String oldFilePath,
        String changeStatus,
        List<PullRequestDiffLine> lines) {

    public PullRequestDiffFile {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(changeStatus, "changeStatus");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        oldFilePath = oldFilePath == null || oldFilePath.isBlank() ? null : oldFilePath;
        changeStatus = changeStatus.trim();
        lines = List.copyOf(lines == null ? List.of() : lines);
    }
}
