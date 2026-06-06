package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpsertPolicyDefaultsCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record PolicyDefaultsHttpRequest(
        Map<String, Object> defaults,
        @JsonbProperty("schema_version")
        int schemaVersion) {

    public UpsertPolicyDefaultsCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new UpsertPolicyDefaultsCommand(requesterUserId, organizationId, defaults, schemaVersion);
    }
}
