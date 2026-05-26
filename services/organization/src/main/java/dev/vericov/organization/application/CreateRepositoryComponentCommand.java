package dev.vericov.organization.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateRepositoryComponentCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        List<String> pathPatterns,
        List<String> owners,
        String criticality,
        Map<String, Object> metadata,
        String status) {
}
