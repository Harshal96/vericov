package dev.vericov.controlplane.application;

import java.util.Map;
import java.util.UUID;

public record UpsertRepositoryConfigCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        Map<String, Object> config,
        int schemaVersion) {
}
