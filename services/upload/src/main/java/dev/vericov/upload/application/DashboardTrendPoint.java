package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardTrendPoint(
        UUID reportId,
        String commitSha,
        Instant createdAt,
        BigDecimal linePct,
        BigDecimal branchPct,
        BigDecimal functionPct,
        BigDecimal statementPct) {
}
