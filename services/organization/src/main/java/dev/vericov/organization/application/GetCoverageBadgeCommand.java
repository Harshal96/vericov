package dev.vericov.organization.application;

import java.util.UUID;

public record GetCoverageBadgeCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String token,
        String branch,
        String metric) {
}
