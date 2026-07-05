package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardRepositoryOverview(
        UUID id,
        String fullName,
        String provider,
        String defaultBranch,
        String visibility,
        String status,
        Instant updatedAt,
        UUID reportId,
        String commitSha,
        Instant reportCreatedAt,
        Integer lineCovered,
        Integer lineTotal,
        Integer branchCovered,
        Integer branchTotal,
        Integer functionCovered,
        Integer functionTotal,
        Integer statementCovered,
        Integer statementTotal,
        BigDecimal lineDelta,
        long reportCount,
        long activeGaps,
        long failingGates) {
}
