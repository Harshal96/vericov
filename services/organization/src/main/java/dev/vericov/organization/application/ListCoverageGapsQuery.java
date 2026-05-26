package dev.vericov.organization.application;

import java.util.UUID;

public record ListCoverageGapsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String commitSha,
        Integer pullRequestNumber,
        UUID componentId,
        String owner,
        String minRisk,
        String riskLevel,
        String status,
        boolean includeDebt,
        int limit) {
}
