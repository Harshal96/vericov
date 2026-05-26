package dev.vericov.organization.application;

import java.time.Instant;
import java.util.UUID;

public record TestRunDetails(
        UUID id,
        UUID tenantId,
        UUID repositoryId,
        UUID uploadId,
        UUID uploadArtifactId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String suiteName,
        int suiteIndex,
        String status,
        int totalCount,
        int passedCount,
        int failedCount,
        int errorCount,
        int skippedCount,
        long durationMs,
        Instant createdAt) {
}
