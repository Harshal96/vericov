package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.UUID;

public record ListCoverageTrendsQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String branch,
        String metric,
        Instant from,
        Instant to,
        int limit) {
}
