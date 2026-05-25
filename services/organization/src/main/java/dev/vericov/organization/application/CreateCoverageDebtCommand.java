package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateCoverageDebtCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID componentId,
        UUID sourceGapId,
        UUID sourceReportId,
        String sourceCommitSha,
        Integer pullRequestNumber,
        String targetType,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        String riskLevel,
        String reason,
        String owner,
        Instant expiresAt,
        String linkedIssueUrl,
        Map<String, Object> metadata) {
}
