package dev.vericov.controlplane.application;

import java.util.UUID;

public record UpdateRepositoryCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String fullName,
        String defaultBranch,
        String visibility,
        String status) {
}
