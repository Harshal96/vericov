package dev.vericov.organization.application;

import java.util.Map;
import java.util.UUID;

public record ValidateRepositoryConfigCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> config,
        int schemaVersion) {
}
