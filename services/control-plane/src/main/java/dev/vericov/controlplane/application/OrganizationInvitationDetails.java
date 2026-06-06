package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationInvitationDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String email,
        String role,
        String status,
        UUID invitedByUserId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant createdAt,
        Instant updatedAt,
        String acceptanceToken) {

    public OrganizationInvitationDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(invitedByUserId, "invitedByUserId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
