package dev.vericov.upload.application;

import dev.vericov.upload.domain.ArtifactKind;

public record StoredArtifact(
        String name,
        ArtifactKind kind,
        String format,
        String contentType,
        long sizeBytes,
        String storageBucket,
        String storagePath,
        String sha256) {
}
