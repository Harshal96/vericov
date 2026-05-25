package dev.vericov.organization.application;

import java.util.UUID;

public record CreateMembershipCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID supabaseUserId,
        String role,
        String status) {
}
