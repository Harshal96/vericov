package dev.vericov.controlplane.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateRepositoryComponentCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID componentId,
        String name,
        String description,
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        Map<String, Object> metadata,
        String status) {
}
