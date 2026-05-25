package dev.vericov.analysis.application.port;

import java.util.Objects;

public record NormalizedCoverageLocation(String bucket, String path) {
    public NormalizedCoverageLocation {
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(path, "path");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
    }
}
