package dev.vericov.controlplane.application;

import java.util.Map;
import java.util.UUID;

public record RepositoryPackageNodeInput(
        UUID componentId,
        String packageName,
        String packagePath,
        String manifestPath,
        String ecosystem,
        Map<String, Object> metadata) {

    public RepositoryPackageNodeInput {
        metadata = ConfigurationValues.deepCopyMap(metadata);
    }
}
