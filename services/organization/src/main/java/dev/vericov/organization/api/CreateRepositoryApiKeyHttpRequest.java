package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateRepositoryApiKeyCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateRepositoryApiKeyHttpRequest(
        String name,
        List<String> scopes,
        @JsonbProperty("branch_allow_patterns")
        List<String> branchAllowPatterns,
        @JsonbProperty("expires_at")
        Instant expiresAt) {

    public CreateRepositoryApiKeyCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new CreateRepositoryApiKeyCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                name,
                scopes,
                branchAllowPatterns,
                expiresAt);
    }
}
