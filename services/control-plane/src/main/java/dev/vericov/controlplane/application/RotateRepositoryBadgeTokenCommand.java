package dev.vericov.controlplane.application;

import java.util.UUID;

public record RotateRepositoryBadgeTokenCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId) {
}
