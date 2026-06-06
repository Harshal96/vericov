package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryGateDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryGateHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        String name,
        @JsonbProperty("gate_type")
        String gateType,
        String metric,
        BigDecimal threshold,
        @JsonbProperty("max_drop")
        BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static RepositoryGateHttpResponse from(RepositoryGateDetails details) {
        return new RepositoryGateHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.name(),
                details.gateType(),
                details.metric(),
                details.threshold(),
                details.maxDrop(),
                details.blocking(),
                details.config(),
                details.status(),
                details.createdAt(),
                details.updatedAt());
    }
}
