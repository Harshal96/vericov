package dev.vericov.organization.application;

import java.util.UUID;

public record GetRepositoryDashboardQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String branch) {
}
