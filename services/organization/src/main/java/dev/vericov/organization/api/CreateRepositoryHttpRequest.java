package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateRepositoryCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record CreateRepositoryHttpRequest(
        String provider,
        @JsonbProperty("provider_repository_id")
        String providerRepositoryId,
        @JsonbProperty("full_name")
        String fullName,
        @JsonbProperty("default_branch")
        String defaultBranch,
        String visibility) {

    public CreateRepositoryCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new CreateRepositoryCommand(
                requesterUserId,
                organizationId,
                provider,
                providerRepositoryId,
                fullName,
                defaultBranch,
                visibility);
    }
}
