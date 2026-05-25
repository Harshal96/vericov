package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RepositoryApiKeyDetails(
        UUID id,
        UUID tenantId,
        UUID repositoryId,
        String name,
        String keyPrefix,
        String keyHash,
        String plaintextKey,
        List<String> scopes,
        List<String> branchAllowPatterns,
        Instant expiresAt,
        Instant revokedAt,
        UUID createdByUserId,
        UUID revokedByUserId,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryApiKeyDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(keyPrefix, "keyPrefix");
        Objects.requireNonNull(keyHash, "keyHash");
        scopes = List.copyOf(scopes == null ? List.of() : scopes);
        branchAllowPatterns = List.copyOf(branchAllowPatterns == null ? List.of() : branchAllowPatterns);
        Objects.requireNonNull(createdByUserId, "createdByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryApiKeyDetails withoutPlaintextKey() {
        return new RepositoryApiKeyDetails(
                id,
                tenantId,
                repositoryId,
                name,
                keyPrefix,
                keyHash,
                null,
                scopes,
                branchAllowPatterns,
                expiresAt,
                revokedAt,
                createdByUserId,
                revokedByUserId,
                lastUsedAt,
                createdAt,
                updatedAt);
    }

    public RepositoryApiKeyDetails revoke(UUID revokedByUserId, Instant revokedAt) {
        return new RepositoryApiKeyDetails(
                id,
                tenantId,
                repositoryId,
                name,
                keyPrefix,
                keyHash,
                null,
                scopes,
                branchAllowPatterns,
                expiresAt,
                revokedAt,
                createdByUserId,
                revokedByUserId,
                lastUsedAt,
                createdAt,
                revokedAt);
    }
}
