package dev.vericov.integrations.application;

import java.util.Map;
import java.util.UUID;

public record CreateIntegrationConnectionCommand(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        String providerKey,
        String displayName,
        String externalAccountId,
        String externalAccountName,
        Map<String, Object> config) {
}
