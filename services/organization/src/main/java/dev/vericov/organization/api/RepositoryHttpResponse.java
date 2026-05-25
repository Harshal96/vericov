package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record RepositoryHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        String provider,
        @JsonbProperty("provider_repository_id")
        String providerRepositoryId,
        @JsonbProperty("full_name")
        String fullName,
        @JsonbProperty("default_branch")
        String defaultBranch,
        String visibility,
        @JsonbProperty("privacy_mode")
        String privacyMode,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryHttpResponse from(RepositoryDetails repository) {
        return new RepositoryHttpResponse(
                repository.id(),
                repository.tenantId(),
                repository.organizationId(),
                repository.provider(),
                repository.providerRepositoryId(),
                repository.fullName(),
                repository.defaultBranch(),
                repository.visibility(),
                repository.privacyMode(),
                repository.status(),
                repository.createdAt(),
                repository.updatedAt());
    }
}
