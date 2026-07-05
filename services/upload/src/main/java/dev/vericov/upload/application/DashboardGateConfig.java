package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.UUID;

public record DashboardGateConfig(
        UUID id,
        String name,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal maxDrop,
        boolean blocking,
        String status) {
}
