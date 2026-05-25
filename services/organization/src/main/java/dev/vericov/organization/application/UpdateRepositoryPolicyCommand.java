package dev.vericov.organization.application;

import java.util.Map;
import java.util.UUID;

public record UpdateRepositoryPolicyCommand(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        UUID policyId,
        String name,
        String description,
        String policyType,
        String targetType,
        String targetSelector,
        Map<String, Object> config,
        String status,
        Integer priority) {
}
