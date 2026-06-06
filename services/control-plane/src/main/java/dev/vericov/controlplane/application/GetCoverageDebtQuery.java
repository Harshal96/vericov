package dev.vericov.controlplane.application;

import java.util.UUID;

public record GetCoverageDebtQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID debtId) {
}
