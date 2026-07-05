package dev.vericov.upload.application;

import java.time.Instant;

public record DashboardUploadArtifact(
        String name,
        String kind,
        String format,
        long sizeBytes,
        Instant createdAt) {
}
