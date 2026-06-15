package dev.vericov.componentconfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ResolvedComponent(
        String key,
        String parentKey,
        List<String> componentPath,
        int depth,
        int position,
        String name,
        List<String> owners,
        Map<String, BigDecimal> effectiveGates,
        List<String> paths) {

    public ResolvedComponent {
        componentPath = List.copyOf(componentPath);
        owners = List.copyOf(owners);
        effectiveGates = Map.copyOf(effectiveGates);
        paths = List.copyOf(paths);
    }

    public boolean leaf() {
        return !paths.isEmpty();
    }
}
