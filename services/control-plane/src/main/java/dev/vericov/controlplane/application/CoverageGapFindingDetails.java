package dev.vericov.controlplane.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CoverageGapFindingDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID coverageReportId,
        UUID pullRequestDiffId,
        UUID componentId,
        String commitSha,
        Integer pullRequestNumber,
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
        Map<String, Object> evidence,
        Instant createdAt,
        Instant updatedAt) {

    public CoverageGapFindingDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(coverageReportId, "coverageReportId");
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(riskScore, "riskScore");
        Objects.requireNonNull(riskLevel, "riskLevel");
        owners = List.copyOf(owners == null ? List.of() : owners);
        Objects.requireNonNull(nextAction, "nextAction");
        Objects.requireNonNull(status, "status");
        evidence = ConfigurationValues.deepCopyMap(evidence);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
