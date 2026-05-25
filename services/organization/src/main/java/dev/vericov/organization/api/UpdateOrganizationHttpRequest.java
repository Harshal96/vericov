package dev.vericov.organization.api;

import dev.vericov.organization.application.UpdateOrganizationCommand;
import java.util.UUID;

public record UpdateOrganizationHttpRequest(
        String name,
        String slug,
        String status) {

    public UpdateOrganizationCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new UpdateOrganizationCommand(requesterUserId, organizationId, name, slug, status);
    }
}
