package dev.vericov.componentconfig;

import java.util.List;

public record ComponentDefinition(
        String key,
        String name,
        List<String> owners,
        ComponentGates gates,
        List<String> paths,
        List<ComponentDefinition> components) {

    public ComponentDefinition {
        name = name == null ? key : name;
        owners = owners == null ? null : List.copyOf(owners);
        gates = gates == null ? ComponentGates.empty() : gates;
        paths = List.copyOf(paths == null ? List.of() : paths);
        components = List.copyOf(components == null ? List.of() : components);
    }

    public boolean leaf() {
        return !paths.isEmpty();
    }
}
