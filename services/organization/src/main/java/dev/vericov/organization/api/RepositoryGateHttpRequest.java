package dev.vericov.organization.api;

import dev.vericov.organization.application.RepositoryGateDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RepositoryGateHttpRequest(
        String name,
        @JsonbProperty("gate_type")
        String gateType,
        String metric,
        BigDecimal threshold,
        @JsonbProperty("max_drop")
        BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status) {

    public RepositoryGateDetails toDetails(
            UUID tenantId,
            UUID organizationId,
            UUID repositoryId,
            Instant now) {
        return new RepositoryGateDetails(
                UUID.randomUUID(),
                tenantId,
                organizationId,
                repositoryId,
                name,
                gateType,
                metric,
                threshold,
                maxDrop,
                blocking,
                config,
                status == null ? "active" : status,
                now,
                now);
    }
}
