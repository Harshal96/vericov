package dev.vericov.controlplane.application;

import java.util.UUID;

public record ListRepositoryDashboardsQuery(
        UUID requesterUserId,
        UUID organizationId,
        String branch) {
}
