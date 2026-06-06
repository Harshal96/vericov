package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpsertRepositoryConfigCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record RepositoryConfigHttpRequest(
        Map<String, Object> config,
        @JsonbProperty("schema_version")
        int schemaVersion) {

    public UpsertRepositoryConfigCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new UpsertRepositoryConfigCommand(requesterUserId, organizationId, repositoryId, config, schemaVersion);
    }
}
