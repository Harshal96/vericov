package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.RepositoryPathResolutionDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.UUID;

public record RepositoryPathResolutionHttpResponse(
        @JsonbProperty("file_path")
        String filePath,
        @JsonbProperty("component_id")
        UUID componentId,
        @JsonbProperty("component_name")
        String componentName,
        @JsonbProperty("package_name")
        String packageName,
        List<String> owners,
        @JsonbProperty("primary_owner")
        String primaryOwner,
        String criticality,
        String source) {

    public static RepositoryPathResolutionHttpResponse from(RepositoryPathResolutionDetails details) {
        return new RepositoryPathResolutionHttpResponse(
                details.filePath(),
                details.componentId(),
                details.componentName(),
                details.packageName(),
                details.owners(),
                details.primaryOwner(),
                details.criticality(),
                details.source());
    }
}
