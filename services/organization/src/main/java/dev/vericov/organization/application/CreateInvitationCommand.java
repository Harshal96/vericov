package dev.vericov.organization.application;

import java.util.UUID;

public record CreateInvitationCommand(
        UUID requesterUserId,
        UUID organizationId,
        String email,
        String role) {
}
