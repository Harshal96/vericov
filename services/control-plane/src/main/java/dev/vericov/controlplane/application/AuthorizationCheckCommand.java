package dev.vericov.controlplane.application;

import java.util.UUID;

public record AuthorizationCheckCommand(
        UUID requesterUserId,
        UUID organizationId,
        String action) {
}
