package dev.vericov.controlplane.application;

import java.util.List;
import java.util.UUID;

public record RepositoryPathResolutionDetails(
        String filePath,
        UUID componentId,
        String componentName,
        String packageName,
        List<String> owners,
        String primaryOwner,
        String criticality,
        String source) {

    public RepositoryPathResolutionDetails {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
