package dev.vericov.analysis.coverage;

import java.util.Objects;

public record CoverageLineHit(String filePath, int lineNumber, long hits) {
    public CoverageLineHit {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        if (hits < 0) {
            throw new IllegalArgumentException("hits must not be negative");
        }
    }
}
