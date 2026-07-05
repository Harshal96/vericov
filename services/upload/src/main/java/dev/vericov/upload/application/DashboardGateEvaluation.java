package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardGateEvaluation(
        UUID id,
        String gateName,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        Instant evaluatedAt,
        UUID coverageReportId) {
}
