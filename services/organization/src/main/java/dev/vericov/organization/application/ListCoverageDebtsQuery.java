package dev.vericov.organization.application;

import java.time.Instant;
import java.util.UUID;

public record ListCoverageDebtsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String status,
        String owner,
        String riskLevel,
        UUID componentId,
        Instant expiresBefore,
        boolean includeExpired,
        UUID sourceGapId,
        int limit) {
}
