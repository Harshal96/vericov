package dev.vericov.controlplane.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RepositoryGateDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryGateDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        config = ConfigurationValues.deepCopyMap(config);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
