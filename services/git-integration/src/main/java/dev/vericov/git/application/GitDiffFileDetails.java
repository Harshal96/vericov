package dev.vericov.git.application;

import java.util.List;
import java.util.Objects;

public record GitDiffFileDetails(
        String filePath,
        String oldFilePath,
        String changeStatus,
        List<GitDiffLineDetails> lines) {

    public GitDiffFileDetails {
        filePath = GitValues.requireTrimmed(filePath, "file_path is required");
        oldFilePath = GitValues.trimOptional(oldFilePath);
        changeStatus = GitValues.requireCanonical(changeStatus, "change_status is required");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }
}
