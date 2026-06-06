package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpsertRepositoryBadgeSettingsCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record RepositoryBadgeSettingsHttpRequest(
        Boolean enabled,
        String branch,
        String metric,
        String label,
        @JsonbProperty("thresholds")
        Map<String, Object> thresholds) {

    public UpsertRepositoryBadgeSettingsCommand toCommand(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId) {
        return new UpsertRepositoryBadgeSettingsCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                enabled,
                branch,
                metric,
                label,
                thresholds);
    }
}
