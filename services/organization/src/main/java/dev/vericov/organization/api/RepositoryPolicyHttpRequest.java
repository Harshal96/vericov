package dev.vericov.organization.api;

import dev.vericov.organization.application.CreateRepositoryPolicyCommand;
import dev.vericov.organization.application.UpdateRepositoryPolicyCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.Map;
import java.util.UUID;

public record RepositoryPolicyHttpRequest(
        String name,
        String description,
        @JsonbProperty("policy_type")
        String policyType,
        @JsonbProperty("target_type")
        String targetType,
        @JsonbProperty("target_selector")
        String targetSelector,
        Map<String, Object> config,
        String status,
        Integer priority) {

    public CreateRepositoryPolicyCommand toCreateCommand(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        return new CreateRepositoryPolicyCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                name,
                description,
                policyType,
                targetType,
                targetSelector,
                config,
                status,
                priority == null ? 100 : priority);
    }

    public UpdateRepositoryPolicyCommand toUpdateCommand(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId,
            UUID policyId) {
        return new UpdateRepositoryPolicyCommand(
                requesterUserId,
                organizationId,
                repositoryId,
                policyId,
                name,
                description,
                policyType,
                targetType,
                targetSelector,
                config,
                status,
                priority);
    }
}
