package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record ResolvedCoverageRef(
        UUID reportId,
        UUID uploadId,
        UUID repositoryId,
        String commitSha,
        String branch,
        Instant createdAt) {
}
