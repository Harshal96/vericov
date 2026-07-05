package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record DashboardReport(
        UUID id,
        UUID uploadId,
        UUID repositoryId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String status,
        Instant createdAt,
        int lineCovered,
        int lineTotal,
        int branchCovered,
        int branchTotal,
        int functionCovered,
        int functionTotal,
        int statementCovered,
        int statementTotal) {
}
