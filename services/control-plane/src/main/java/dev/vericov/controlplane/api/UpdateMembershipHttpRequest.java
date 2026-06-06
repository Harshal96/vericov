package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpdateMembershipCommand;
import java.util.UUID;

public record UpdateMembershipHttpRequest(
        String role,
        String status) {

    public UpdateMembershipCommand toCommand(UUID requesterUserId, UUID organizationId, UUID membershipId) {
        return new UpdateMembershipCommand(requesterUserId, organizationId, membershipId, role, status);
    }
}
