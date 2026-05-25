package dev.vericov.organization.application;

import java.util.UUID;

public record AuthorizationCheckCommand(
        UUID requesterUserId,
        UUID organizationId,
        String action) {
}
