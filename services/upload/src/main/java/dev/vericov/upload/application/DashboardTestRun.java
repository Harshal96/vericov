package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record DashboardTestRun(
        UUID id,
        String suiteName,
        String status,
        int totalCount,
        int passedCount,
        int failedCount,
        int errorCount,
        int skippedCount,
        long durationMs,
        String commitSha,
        String branch,
        Instant createdAt) {
}
