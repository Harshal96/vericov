package dev.vericov.componentconfig;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public record ComponentGates(Map<String, BigDecimal> thresholds) {
    public ComponentGates {
        thresholds = Map.copyOf(thresholds == null ? Map.of() : new TreeMap<>(thresholds));
    }

    public static ComponentGates empty() {
        return new ComponentGates(Map.of());
    }

    public ComponentGates overlay(ComponentGates overrides) {
        TreeMap<String, BigDecimal> combined = new TreeMap<>(thresholds);
        combined.putAll(overrides.thresholds);
        return new ComponentGates(combined);
    }
}
