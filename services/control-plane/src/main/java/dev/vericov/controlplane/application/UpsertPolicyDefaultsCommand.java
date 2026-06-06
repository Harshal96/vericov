package dev.vericov.controlplane.application;

import java.util.Map;
import java.util.UUID;

public record UpsertPolicyDefaultsCommand(
        UUID requesterUserId,
        UUID organizationId,
        Map<String, Object> defaults,
        int schemaVersion) {
}
