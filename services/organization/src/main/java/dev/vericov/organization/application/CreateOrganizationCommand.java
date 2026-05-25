package dev.vericov.organization.application;

import java.util.UUID;

public record CreateOrganizationCommand(
        UUID requesterUserId,
        String name,
        String slug,
        String plan) {
}
