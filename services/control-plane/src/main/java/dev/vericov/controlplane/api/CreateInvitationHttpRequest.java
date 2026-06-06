package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CreateInvitationCommand;
import java.util.UUID;

public record CreateInvitationHttpRequest(
        String email,
        String role) {

    public CreateInvitationCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new CreateInvitationCommand(requesterUserId, organizationId, email, role);
    }
}
