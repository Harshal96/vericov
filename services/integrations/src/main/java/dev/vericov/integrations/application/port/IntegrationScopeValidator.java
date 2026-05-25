package dev.vericov.integrations.application.port;

import java.util.UUID;

public interface IntegrationScopeValidator {
    void requireScope(UUID tenantId, UUID orgId, String scopeType, UUID scopeId);
}
