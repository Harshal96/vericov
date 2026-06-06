package dev.vericov.controlplane.application;

import java.util.UUID;

public record UpdateOrganizationCommand(
        UUID requesterUserId,
        UUID organizationId,
        String name,
        String slug,
        String status) {
}
