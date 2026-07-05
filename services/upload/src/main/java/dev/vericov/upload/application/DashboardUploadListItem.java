package dev.vericov.upload.application;

import java.time.Instant;
import java.util.UUID;

public record DashboardUploadListItem(
        UUID id,
        UUID repositoryId,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        String ciProvider,
        String ciBuildUrl,
        String status,
        Instant createdAt,
        Instant completedAt,
        UUID coverageReportId,
        String errorCode,
        String errorMessage) {
}
