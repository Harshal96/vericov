package dev.vericov.controlplane.application;

import java.util.UUID;

public record ListGateEvaluationsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String branch,
        String status,
        int limit) {
}
