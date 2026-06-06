package dev.vericov.controlplane.application;

import java.util.UUID;

public record AcceptInvitationCommand(
        UUID acceptingUserId,
        String acceptingEmail,
        UUID organizationId,
        UUID invitationId,
        String acceptanceToken) {
}
