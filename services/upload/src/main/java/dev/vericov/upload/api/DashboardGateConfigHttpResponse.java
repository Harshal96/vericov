package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardGateConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record DashboardGateConfigHttpResponse(
        UUID id,
        String name,
        @JsonbProperty("gate_type") String gateType,
        String metric,
        BigDecimal threshold,
        @JsonbProperty("max_drop") BigDecimal maxDrop,
        boolean blocking,
        String status) {

    public static DashboardGateConfigHttpResponse from(DashboardGateConfig config) {
        return new DashboardGateConfigHttpResponse(
                config.id(),
                config.name(),
                config.gateType(),
                config.metric(),
                config.threshold(),
                config.maxDrop(),
                config.blocking(),
                config.status());
    }
}
