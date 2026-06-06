package dev.vericov.controlplane.application;

import java.util.UUID;

public record GetCommitCoverageReportQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        boolean includeFiles,
        int fileLimit) {
}
