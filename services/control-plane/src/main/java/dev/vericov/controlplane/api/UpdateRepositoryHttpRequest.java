package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpdateRepositoryCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record UpdateRepositoryHttpRequest(
        @JsonbProperty("full_name")
        String fullName,
        @JsonbProperty("default_branch")
        String defaultBranch,
        String visibility,
        String status) {

    public UpdateRepositoryCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new UpdateRepositoryCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                fullName,
                defaultBranch,
                visibility,
                status);
    }
}
