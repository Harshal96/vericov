package dev.vericov.analysis.gates;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryComponentContext(
        UUID componentId,
        String name,
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        Map<String, Object> metadata) {

    public RepositoryComponentContext {
        pathPatterns = List.copyOf(pathPatterns == null ? List.of() : pathPatterns);
        owners = List.copyOf(owners == null ? List.of() : owners);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
