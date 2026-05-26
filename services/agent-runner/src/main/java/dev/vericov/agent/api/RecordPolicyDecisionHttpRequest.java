package dev.vericov.agent.api;

import dev.vericov.agent.application.RecordPolicyDecisionCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.Map;

public record RecordPolicyDecisionHttpRequest(
        @JsonbProperty("tenant_id") String tenantId,
        @JsonbProperty("repository_id") String repositoryId,
        String decision,
        @JsonbProperty("matched_policy_ids") List<String> matchedPolicyIds,
        String action,
        Map<String, Object> resource,
        String reason) {
    public RecordPolicyDecisionCommand toCommand(String taskId) {
        return new RecordPolicyDecisionCommand(
                ApiParsing.parseRequiredUuid(tenantId, "tenant_id"),
                ApiParsing.parseRequiredUuid(repositoryId, "repository_id"),
                ApiParsing.parseRequiredUuid(taskId, "task_id"),
                decision,
                matchedPolicyIds == null || matchedPolicyIds.isEmpty()
                        ? List.of()
                        : ApiParsing.parseUuidList(matchedPolicyIds, "matched_policy_ids"),
                action,
                resource,
                reason);
    }
}
