package dev.vericov.controlplane.application;

import java.util.UUID;

public record GetCoverageLineHitsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        String filePath) {
}
