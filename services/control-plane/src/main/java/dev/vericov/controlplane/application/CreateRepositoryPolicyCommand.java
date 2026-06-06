package dev.vericov.controlplane.application;

import java.util.Map;
import java.util.UUID;

public record CreateRepositoryPolicyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        int priority) {
}
