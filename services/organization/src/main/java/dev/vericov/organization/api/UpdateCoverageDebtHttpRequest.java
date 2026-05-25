package dev.vericov.organization.api;

import dev.vericov.organization.application.UpdateCoverageDebtCommand;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdateCoverageDebtHttpRequest(
        String owner,
        String riskLevel,
        String reason,
        Instant expiresAt,
        String linkedIssueUrl,
        Map<String, Object> metadata) {

    public UpdateCoverageDebtCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId, UUID debtId) {
        return new UpdateCoverageDebtCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                debtId,
                owner,
                riskLevel,
                reason,
                expiresAt,
                linkedIssueUrl,
                metadata
        );
    }
}
