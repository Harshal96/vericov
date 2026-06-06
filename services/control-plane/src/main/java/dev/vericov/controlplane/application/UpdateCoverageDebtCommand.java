package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdateCoverageDebtCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID debtId,
        String owner,
        String riskLevel,
        String reason,
        Instant expiresAt,
        String linkedIssueUrl,
        Map<String, Object> metadata) {
}
