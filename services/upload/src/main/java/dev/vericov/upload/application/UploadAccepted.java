package dev.vericov.upload.application;

import dev.vericov.upload.domain.UploadStatus;
import java.util.UUID;

public record UploadAccepted(
        UUID uploadId,
        UploadStatus status,
        String pollUrl,
        UUID repositoryId,
        String commitSha,
        UUID analysisJobId) {
}
