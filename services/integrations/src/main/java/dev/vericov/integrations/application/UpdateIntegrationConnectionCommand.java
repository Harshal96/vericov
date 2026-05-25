package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdateIntegrationConnectionCommand(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String displayName,
        String status,
        Map<String, Object> config,
        Instant expectedUpdatedAt) {
}
