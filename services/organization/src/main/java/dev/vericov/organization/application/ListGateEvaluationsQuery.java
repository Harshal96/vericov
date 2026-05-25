package dev.vericov.organization.application;

import java.util.UUID;

public record ListGateEvaluationsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String branch,
        String status,
        int limit) {
}
