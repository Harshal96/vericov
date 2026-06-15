package dev.vericov.componentconfig;

import java.util.List;

public record ComponentAssignment(
        String leafComponentKey,
        List<String> componentPath,
        List<String> owners) {

    public ComponentAssignment {
        componentPath = List.copyOf(componentPath);
        owners = List.copyOf(owners);
    }
}
