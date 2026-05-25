package dev.vericov.organization.application;

import java.util.UUID;

public record CreateRepositoryCommand(
        UUID requesterUserId,
        UUID organizationId,
        String provider,
        String providerRepositoryId,
        String fullName,
        String defaultBranch,
        String visibility) {
}
