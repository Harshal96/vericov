package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateOrganizationCommand;
import java.util.UUID;

public record CreateOrganizationHttpRequest(
        String name,
        String slug,
        String plan) {

    public CreateOrganizationCommand toCommand(UUID requesterUserId) {
        return new CreateOrganizationCommand(requesterUserId, name, slug, plan);
    }
}
