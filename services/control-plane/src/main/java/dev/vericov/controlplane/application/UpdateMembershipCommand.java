package dev.vericov.controlplane.application;

import java.util.UUID;

public record UpdateMembershipCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID membershipId,
        String role,
        String status) {
}
