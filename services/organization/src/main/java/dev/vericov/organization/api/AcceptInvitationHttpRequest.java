package dev.vericov.organization.api;

import dev.vericov.organization.application.AcceptInvitationCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public record AcceptInvitationHttpRequest(
        @JsonbProperty("acceptance_token")
        String acceptanceToken) {

    public AcceptInvitationCommand toCommand(
            UUID acceptingUserId,
            String acceptingEmail,
            UUID organizationId,
            UUID invitationId) {
        return new AcceptInvitationCommand(
                acceptingUserId,
                acceptingEmail,
                organizationId,
                invitationId,
                acceptanceToken);
    }
}
