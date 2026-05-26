package dev.vericov.analysis.gates;

import java.util.Map;
import java.util.UUID;

public record RepositoryPackageNodeContext(
        UUID componentId,
        String packageName,
        String packagePath,
        String manifestPath,
        String ecosystem,
        Map<String, Object> metadata) {

    public RepositoryPackageNodeContext {
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
