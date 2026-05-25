package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateInvitationCommand;
import java.util.UUID;

public record CreateInvitationHttpRequest(
        String email,
        String role) {

    public CreateInvitationCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new CreateInvitationCommand(requesterUserId, organizationId, email, role);
    }
}
