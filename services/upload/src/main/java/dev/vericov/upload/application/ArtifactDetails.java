package dev.vericov.upload.application;

import dev.vericov.upload.domain.ArtifactKind;

public record ArtifactDetails(
        String name,
        ArtifactKind kind,
        String format,
        String status,
        long sizeBytes) {
}
