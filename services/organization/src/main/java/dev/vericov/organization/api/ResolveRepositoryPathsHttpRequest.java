package dev.vericov.organization.api;

import dev.vericov.organization.application.ResolveRepositoryPathsCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.UUID;

public record ResolveRepositoryPathsHttpRequest(
        @JsonbProperty("file_paths")
        List<String> filePaths) {

    public ResolveRepositoryPathsCommand toCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new ResolveRepositoryPathsCommand(requesterUserId, organizationId, repositoryId, filePaths);
    }
}
