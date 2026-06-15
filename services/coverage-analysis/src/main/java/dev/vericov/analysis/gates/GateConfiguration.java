package dev.vericov.analysis.gates;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GateConfiguration(
        UUID id,
        UUID tenantId,
        UUID repositoryId,
        String name,
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal maxDrop,
        boolean blocking,
        Map<String, Object> config,
        String status) {

    public GateConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(gateType, "gateType");
        Objects.requireNonNull(metric, "metric");
        config = copyMap(config);
        Objects.requireNonNull(status, "status");
    }

    public boolean active() {
        return "active".equals(status);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
