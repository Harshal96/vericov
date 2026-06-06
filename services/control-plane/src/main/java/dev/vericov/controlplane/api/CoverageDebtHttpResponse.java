package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageDebtDetails;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CoverageDebtHttpResponse(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID componentId,
        UUID sourceGapId,
        UUID sourceReportId,
        String sourceCommitSha,
        Integer pullRequestNumber,
        String targetType,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        String riskLevel,
        String reason,
        String owner,
        String status,
        Instant expiresAt,
        Instant resolvedAt,
        UUID resolvedByUserId,
        Instant revokedAt,
        UUID revokedByUserId,
        String linkedIssueUrl,
        Map<String, Object> metadata,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static CoverageDebtHttpResponse from(CoverageDebtDetails debt) {
        return new CoverageDebtHttpResponse(
                debt.id(),
                debt.tenantId(),
                debt.organizationId(),
                debt.repositoryId(),
                debt.componentId(),
                debt.sourceGapId(),
                debt.sourceReportId(),
                debt.sourceCommitSha(),
                debt.pullRequestNumber(),
                debt.targetType(),
                debt.filePath(),
                debt.lineStart(),
                debt.lineEnd(),
                debt.symbolName(),
                debt.riskLevel(),
                debt.reason(),
                debt.owner(),
                debt.status(),
                debt.expiresAt(),
                debt.resolvedAt(),
                debt.resolvedByUserId(),
                debt.revokedAt(),
                debt.revokedByUserId(),
                debt.linkedIssueUrl(),
                debt.metadata(),
                debt.createdByUserId(),
                debt.createdAt(),
                debt.updatedAt()
        );
    }
}
