package dev.vericov.controlplane.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CoverageDebtEventDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        UUID debtItemId,
        String eventType,
        UUID actorUserId,
        Map<String, Object> payload,
        Instant createdAt) {

    public CoverageDebtEventDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(debtItemId, "debtItemId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(createdAt, "createdAt");
        payload = ConfigurationValues.deepCopyMap(payload);
    }
}
