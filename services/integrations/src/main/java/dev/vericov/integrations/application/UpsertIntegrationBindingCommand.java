package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpsertIntegrationBindingCommand(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        String scopeType,
        UUID scopeId,
        List<String> capabilities,
        Map<String, Object> config,
        String status,
        Instant expectedUpdatedAt) {

    public UpsertIntegrationBindingCommand(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String scopeType,
            UUID scopeId,
            List<String> capabilities,
            Map<String, Object> config,
            String status) {
        this(
                requesterUserId,
                tenantId,
                orgId,
                connectionId,
                scopeType,
                scopeId,
                capabilities,
                config,
                status,
                null);
    }
}
