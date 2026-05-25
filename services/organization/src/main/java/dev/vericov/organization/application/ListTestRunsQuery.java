package dev.vericov.organization.application;

import java.util.UUID;

public record ListTestRunsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        int limit) {
}
