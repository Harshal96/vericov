package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationInvitation(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        String email,
        String role,
        String status,
        UUID invitedByUserId,
        String acceptanceTokenHash,
        Instant expiresAt,
        Instant acceptedAt,
        Instant createdAt,
        Instant updatedAt) {

    public OrganizationInvitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(invitedByUserId, "invitedByUserId");
        Objects.requireNonNull(acceptanceTokenHash, "acceptanceTokenHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public OrganizationInvitation accept(Instant acceptedAt) {
        return new OrganizationInvitation(
                id,
                tenantId,
                organizationId,
                email,
                role,
                "accepted",
                invitedByUserId,
                acceptanceTokenHash,
                expiresAt,
                acceptedAt,
                createdAt,
                acceptedAt);
    }

    public OrganizationInvitationDetails toDetails(String acceptanceToken) {
        return new OrganizationInvitationDetails(
                id,
                tenantId,
                organizationId,
                email,
                role,
                status,
                invitedByUserId,
                expiresAt,
                acceptedAt,
                createdAt,
                updatedAt,
                acceptanceToken);
    }
}
