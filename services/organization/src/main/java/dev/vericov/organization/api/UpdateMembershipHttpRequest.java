package dev.vericov.organization.api;

import dev.vericov.organization.application.UpdateMembershipCommand;
import java.util.UUID;

public record UpdateMembershipHttpRequest(
        String role,
        String status) {

    public UpdateMembershipCommand toCommand(UUID requesterUserId, UUID organizationId, UUID membershipId) {
        return new UpdateMembershipCommand(requesterUserId, organizationId, membershipId, role, status);
    }
}
