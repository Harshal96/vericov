package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CreateCoverageDebtCommand;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateCoverageDebtHttpRequest(
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

    public CreateCoverageDebtCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new CreateCoverageDebtCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                null,
                sourceGapId,
                sourceReportId,
                sourceCommitSha,
                pullRequestNumber,
                targetType,
                filePath,
                lineStart,
                lineEnd,
                symbolName,
                riskLevel,
                reason,
                owner,
                expiresAt,
                linkedIssueUrl,
                metadata
        );
    }
}
