package dev.vericov.organization.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryOwnerRuleDetails(
        UUID id,
        UUID tenantId,
        UUID organizationId,
        UUID repositoryId,
        String source,
        String pattern,
        List<String> owners,
        int priority,
        String sourceRef,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public RepositoryOwnerRuleDetails {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
