package dev.vericov.organization.api;

import dev.vericov.organization.application.OrganizationInvitationDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record InvitationHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        String email,
        String role,
        String status,
        @JsonbProperty("invited_by_user_id")
        UUID invitedByUserId,
        @JsonbProperty("expires_at")
        Instant expiresAt,
        @JsonbProperty("accepted_at")
        Instant acceptedAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt,
        @JsonbProperty("acceptance_token")
        String acceptanceToken) {

    public static InvitationHttpResponse from(OrganizationInvitationDetails invitation) {
        return new InvitationHttpResponse(
                invitation.id(),
                invitation.tenantId(),
                invitation.organizationId(),
                invitation.email(),
                invitation.role(),
                invitation.status(),
                invitation.invitedByUserId(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.createdAt(),
                invitation.updatedAt(),
                invitation.acceptanceToken());
    }
}
