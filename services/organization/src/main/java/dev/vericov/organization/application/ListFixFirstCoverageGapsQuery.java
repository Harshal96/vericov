package dev.vericov.organization.application;

import java.util.UUID;

public record ListFixFirstCoverageGapsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        Integer pullRequestNumber,
        boolean includeSourceRequired,
        int limit) {
}
