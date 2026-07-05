package dev.vericov.upload.application;

import java.math.BigDecimal;

public record DashboardOverview(
        long repoCount,
        long activeRepoCount,
        BigDecimal weightedLineCoverage,
        long totalReports,
        long activeGaps,
        long criticalGaps,
        long failingGates) {
}
