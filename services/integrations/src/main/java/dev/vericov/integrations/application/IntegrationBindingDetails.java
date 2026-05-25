package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IntegrationBindingDetails(
        UUID id,
        UUID tenantId,
        UUID connectionId,
        String scopeType,
        UUID scopeId,
        List<String> capabilities,
        Map<String, Object> config,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationBindingDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(connectionId, "connectionId");
        scopeType = IntegrationConfigValues.requireCanonical(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
        status = IntegrationConfigValues.requireCanonical(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        config = IntegrationConfigValues.deepCopyMap(config);
    }

    public IntegrationBindingDetails withStatus(String nextStatus, Instant updatedAt) {
        return new IntegrationBindingDetails(
                id,
                tenantId,
                connectionId,
                scopeType,
                scopeId,
                capabilities,
                config,
                nextStatus,
                createdAt,
                updatedAt);
    }
}
