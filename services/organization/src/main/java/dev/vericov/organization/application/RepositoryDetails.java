package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepositoryDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String provider,
        String providerRepositoryId,
        String fullName,
        String defaultBranch,
        String visibility,
        String privacyMode,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerRepositoryId, "providerRepositoryId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(defaultBranch, "defaultBranch");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(privacyMode, "privacyMode");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RepositoryDetails withValues(
            String nextFullName,
            String nextDefaultBranch,
            String nextVisibility,
            String nextStatus,
            Instant nextUpdatedAt) {
        return new RepositoryDetails(
                id,
                tenantId,
                organizationId,
                provider,
                providerRepositoryId,
                nextFullName,
                nextDefaultBranch,
                nextVisibility,
                privacyMode,
                nextStatus,
                createdAt,
                nextUpdatedAt);
    }
}
