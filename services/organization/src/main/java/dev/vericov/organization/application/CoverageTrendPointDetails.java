package dev.vericov.organization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CoverageTrendPointDetails(
        UUID reportId,
        String commitSha,
        String branch,
        String metric,
        BigDecimal percent,
        Instant createdAt) {
}
