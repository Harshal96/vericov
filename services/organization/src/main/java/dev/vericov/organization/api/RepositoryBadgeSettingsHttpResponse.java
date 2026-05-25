package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryBadgeSettingsDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryBadgeSettingsHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        boolean enabled,
        String branch,
        String metric,
        String label,
        Map<String, Object> thresholds,
        @JsonbProperty("token_prefix")
        String tokenPrefix,
        @JsonbProperty("created_by_user_id")
        UUID createdByUserId,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt,
        @JsonbProperty("revoked_at")
        Instant revokedAt) {

    public static RepositoryBadgeSettingsHttpResponse from(RepositoryBadgeSettingsDetails details) {
        return new RepositoryBadgeSettingsHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.enabled(),
                details.branch(),
                details.metric(),
                details.label(),
                details.thresholds(),
                details.tokenPrefix(),
                details.createdByUserId(),
                details.createdAt(),
                details.updatedAt(),
                details.revokedAt());
    }
}
