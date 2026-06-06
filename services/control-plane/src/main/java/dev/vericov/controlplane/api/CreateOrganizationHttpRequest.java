package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CreateOrganizationCommand;
import java.util.UUID;

public record CreateOrganizationHttpRequest(
        String name,
        String slug,
        String plan) {

    public CreateOrganizationCommand toCommand(UUID requesterUserId) {
        return new CreateOrganizationCommand(requesterUserId, name, slug, plan);
    }
}
