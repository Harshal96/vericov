package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryApiKeyDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryApiKeyHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String name,
        @JsonbProperty("key_prefix")
        String keyPrefix,
        @JsonbProperty("api_key")
        String apiKey,
        List<String> scopes,
        @JsonbProperty("branch_allow_patterns")
        List<String> branchAllowPatterns,
        @JsonbProperty("expires_at")
        Instant expiresAt,
        @JsonbProperty("revoked_at")
        Instant revokedAt,
        @JsonbProperty("last_used_at")
        Instant lastUsedAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryApiKeyHttpResponse from(RepositoryApiKeyDetails apiKey) {
        return new RepositoryApiKeyHttpResponse(
                apiKey.id(),
                apiKey.tenantId(),
                apiKey.repositoryId(),
                apiKey.name(),
                apiKey.keyPrefix(),
                apiKey.plaintextKey(),
                apiKey.scopes(),
                apiKey.branchAllowPatterns(),
                apiKey.expiresAt(),
                apiKey.revokedAt(),
                apiKey.lastUsedAt(),
                apiKey.createdAt(),
                apiKey.updatedAt());
    }
}
