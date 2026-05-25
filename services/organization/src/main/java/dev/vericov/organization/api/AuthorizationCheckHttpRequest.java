package dev.vericov.organization.api;

import dev.vericov.organization.application.AuthorizationCheckCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record AuthorizationCheckHttpRequest(
        @JsonbProperty("org_id")
        UUID organizationId,
        String action) {

    public AuthorizationCheckCommand toCommand(UUID requesterUserId) {
        return new AuthorizationCheckCommand(requesterUserId, organizationId, action);
    }
}
