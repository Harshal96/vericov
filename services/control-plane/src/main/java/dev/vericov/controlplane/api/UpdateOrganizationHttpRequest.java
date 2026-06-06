package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.UpdateOrganizationCommand;
import java.util.UUID;

public record UpdateOrganizationHttpRequest(
        String name,
        String slug,
        String status) {

    public UpdateOrganizationCommand toCommand(UUID requesterUserId, UUID organizationId) {
        return new UpdateOrganizationCommand(requesterUserId, organizationId, name, slug, status);
    }
}
