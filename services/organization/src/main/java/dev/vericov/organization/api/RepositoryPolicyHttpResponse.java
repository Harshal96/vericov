package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryPolicyDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryPolicyHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String name,
        String description,
        @JsonbProperty("policy_type")
        String policyType,
        @JsonbProperty("target_type")
        String targetType,
        @JsonbProperty("target_selector")
        String targetSelector,
        Map<String, Object> config,
        String status,
        int priority,
        @JsonbProperty("created_by_user_id")
        UUID createdByUserId,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryPolicyHttpResponse from(RepositoryPolicyDetails details) {
        return new RepositoryPolicyHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.name(),
                details.description(),
                details.policyType(),
                details.targetType(),
                details.targetSelector(),
                details.config(),
                details.status(),
                details.priority(),
                details.createdByUserId(),
                details.createdAt(),
                details.updatedAt());
    }
}
