package dev.vericov.analysis.diff;

import java.util.Objects;

public record PullRequestDiffLine(Integer baseLineNumber, Integer headLineNumber, DiffLineType type) {
    public PullRequestDiffLine {
        Objects.requireNonNull(type, "type");
        if (baseLineNumber != null && baseLineNumber < 1) {
            throw new IllegalArgumentException("baseLineNumber must be positive");
        }
        if (headLineNumber != null && headLineNumber < 1) {
            throw new IllegalArgumentException("headLineNumber must be positive");
        }
    }
}
