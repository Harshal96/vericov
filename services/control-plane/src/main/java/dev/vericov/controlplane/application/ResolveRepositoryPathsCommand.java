package dev.vericov.controlplane.application;

import java.util.List;
import java.util.UUID;

public record ResolveRepositoryPathsCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        List<String> filePaths) {

    public ResolveRepositoryPathsCommand {
        filePaths = List.copyOf(filePaths == null ? List.of() : filePaths);
    }
}
