package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryOwnerRuleDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryOwnerRuleHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String source,
        String pattern,
        List<String> owners,
        int priority,
        @JsonbProperty("source_ref")
        String sourceRef,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryOwnerRuleHttpResponse from(RepositoryOwnerRuleDetails details) {
        return new RepositoryOwnerRuleHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.source(),
                details.pattern(),
                details.owners(),
                details.priority(),
                details.sourceRef(),
                details.status(),
                details.createdAt(),
                details.updatedAt());
    }
}
