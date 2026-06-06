package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PolicyDefaultsDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        Map<String, Object> defaults,
        int schemaVersion,
        UUID updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public PolicyDefaultsDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        defaults = ConfigurationValues.deepCopyMap(defaults);
        Objects.requireNonNull(updatedByUserId, "updatedByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public PolicyDefaultsDetails withValues(
            Map<String, Object> nextDefaults,
            int nextSchemaVersion,
            UUID nextUpdatedByUserId,
            Instant nextUpdatedAt) {
        return new PolicyDefaultsDetails(
                id,
                tenantId,
                organizationId,
                nextDefaults,
                nextSchemaVersion,
                nextUpdatedByUserId,
                createdAt,
                nextUpdatedAt);
    }
}
