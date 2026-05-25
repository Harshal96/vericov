package dev.vericov.analysis.application.port;

import dev.vericov.analysis.gates.RepositoryContext;
import java.util.UUID;

public interface RepositoryContextRepository {
    RepositoryContext loadContext(
            UUID tenantId,
            UUID repositoryId,
            String commitSha,
            String branch,
            Integer pullRequestNumber);
}
