package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IntegrationConnectionDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        String providerKey,
        String integrationType,
        String displayName,
        String externalAccountId,
        String externalAccountName,
        String status,
        Map<String, Object> config,
        UUID createdBy,
        Instant lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationConnectionDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        providerKey = IntegrationConfigValues.requireCanonical(providerKey, "providerKey");
        integrationType = IntegrationConfigValues.requireCanonical(integrationType, "integrationType");
        displayName = IntegrationConfigValues.requireTrimmed(displayName, "displayName");
        externalAccountId = IntegrationConfigValues.requireTrimmed(externalAccountId, "externalAccountId");
        externalAccountName = externalAccountName == null ? null : externalAccountName.trim();
        status = IntegrationConfigValues.requireCanonical(status, "status");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        config = IntegrationConfigValues.deepCopyMap(config);
    }

    public IntegrationConnectionDetails withStatus(String nextStatus, Instant updatedAt) {
        return new IntegrationConnectionDetails(
                id,
                tenantId,
                orgId,
                providerKey,
                integrationType,
                displayName,
                externalAccountId,
                externalAccountName,
                nextStatus,
                config,
                createdBy,
                lastVerifiedAt,
                createdAt,
                updatedAt);
    }

    public IntegrationConnectionDetails withValues(
            String nextDisplayName,
            String nextStatus,
            Map<String, Object> nextConfig,
            Instant updatedAt) {
        return new IntegrationConnectionDetails(
                id,
                tenantId,
                orgId,
                providerKey,
                integrationType,
                nextDisplayName,
                externalAccountId,
                externalAccountName,
                nextStatus,
                nextConfig,
                createdBy,
                lastVerifiedAt,
                createdAt,
                updatedAt);
    }
}
