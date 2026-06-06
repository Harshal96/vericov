package dev.vericov.controlplane.application;

import java.util.UUID;

public record RevokeRepositoryApiKeyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID apiKeyId) {
}
