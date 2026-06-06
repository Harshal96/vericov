package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MembershipDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID supabaseUserId,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public MembershipDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(supabaseUserId, "supabaseUserId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public MembershipDetails withValues(String nextRole, String nextStatus, Instant updatedAt) {
        return new MembershipDetails(
                id,
                tenantId,
                organizationId,
                supabaseUserId,
                nextRole,
                nextStatus,
                createdAt,
                updatedAt);
    }
}
