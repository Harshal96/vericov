package dev.vericov.integrations.application.port;

import java.util.UUID;

public interface IntegrationAuthorizer {
    void requireOrgAccess(UUID requesterUserId, UUID tenantId, UUID orgId, String action);
}
