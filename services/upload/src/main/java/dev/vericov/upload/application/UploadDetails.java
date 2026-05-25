package dev.vericov.upload.application;

import dev.vericov.upload.domain.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadDetails(
        UUID uploadId,
        UUID repositoryId,
        String commitSha,
        UploadStatus status,
        UUID analysisJobId,
        List<ArtifactDetails> artifacts,
        Instant createdAt) {

    public UploadDetails {
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }
}
