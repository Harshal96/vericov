package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CreateRepositoryComponentCommand;
import dev.vericov.controlplane.application.UpdateRepositoryComponentCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryComponentHttpRequest(
        String name,
        String description,
        @JsonbProperty("path_patterns")
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        @JsonbProperty("metadata")
        Map<String, Object> metadata,
        String status) {

    public CreateRepositoryComponentCommand toCreateCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new CreateRepositoryComponentCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                name,
                description,
                pathPatterns,
                owners,
                criticality,
                metadata,
                status);
    }

    public UpdateRepositoryComponentCommand toUpdateCommand(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId,
            UUID componentId) {
        return new UpdateRepositoryComponentCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                componentId,
                name,
                description,
                pathPatterns,
                owners,
                criticality,
                metadata,
                status);
    }
}
