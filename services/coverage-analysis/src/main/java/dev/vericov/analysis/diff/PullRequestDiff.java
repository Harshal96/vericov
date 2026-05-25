package dev.vericov.analysis.diff;

import java.util.List;
import java.util.Objects;

public record PullRequestDiff(String baseSha, String headSha, List<PullRequestDiffFile> files) {
    public PullRequestDiff {
        Objects.requireNonNull(baseSha, "baseSha");
        Objects.requireNonNull(headSha, "headSha");
        if (baseSha.isBlank()) {
            throw new IllegalArgumentException("baseSha is required");
        }
        if (headSha.isBlank()) {
            throw new IllegalArgumentException("headSha is required");
        }
        files = List.copyOf(files == null ? List.of() : files);
    }
}
