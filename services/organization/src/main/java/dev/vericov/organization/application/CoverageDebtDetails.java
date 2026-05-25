package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CoverageDebtDetails(
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

    public CoverageDebtDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(sourceCommitSha, "sourceCommitSha");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdByUserId, "createdByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        metadata = ConfigurationValues.deepCopyMap(metadata);
    }

    public String effectiveStatus(Instant now) {
        if ("active".equals(status) && !expiresAt.isAfter(now)) {
            return "expired";
        }
        return status;
    }

    public CoverageDebtDetails normalized(Instant now) {
        String eff = effectiveStatus(now);
        if (eff.equals(status)) {
            return this;
        }
        return new CoverageDebtDetails(
                id,
                tenantId,
                organizationId,
                repositoryId,
                componentId,
                sourceGapId,
                sourceReportId,
                sourceCommitSha,
                pullRequestNumber,
                targetType,
                filePath,
                lineStart,
                lineEnd,
                symbolName,
                riskLevel,
                reason,
                owner,
                eff,
                expiresAt,
                resolvedAt,
                resolvedByUserId,
                revokedAt,
                revokedByUserId,
                linkedIssueUrl,
                metadata,
                createdByUserId,
                createdAt,
                updatedAt);
    }
}
