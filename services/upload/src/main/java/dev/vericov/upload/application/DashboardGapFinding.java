package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardGapFinding(
        UUID id,
        UUID coverageReportId,
        UUID repositoryId,
        String filePath,
        String targetType,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        String reasonCode,
        String explanation,
        String confidence,
        BigDecimal riskScore,
        String riskLevel,
        List<String> owners,
        String nextAction,
        String status,
        String commitSha,
        Integer pullRequestNumber) {

    public DashboardGapFinding {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
