package dev.vericov.organization.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationDetails(
        UUID id,
        UUID tenantId,
        String name,
        String slug,
        String plan,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public OrganizationDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public OrganizationDetails withValues(String nextName, String nextSlug, String nextStatus, Instant updatedAt) {
        return new OrganizationDetails(
                id,
                tenantId,
                nextName,
                nextSlug,
                plan,
                nextStatus,
                createdAt,
                updatedAt);
    }
}
