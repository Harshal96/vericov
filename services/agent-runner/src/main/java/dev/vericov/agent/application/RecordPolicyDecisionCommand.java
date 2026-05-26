package dev.vericov.agent.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecordPolicyDecisionCommand(
        UUID tenantId,
        UUID repositoryId,
        UUID taskId,
        String decision,
        List<UUID> matchedPolicyIds,
        String action,
        Map<String, Object> resource,
        String reason) {
    public RecordPolicyDecisionCommand {
        tenantId = AgentValues.requireUuid(tenantId, "tenant_id");
        repositoryId = AgentValues.requireUuid(repositoryId, "repository_id");
        taskId = AgentValues.requireUuid(taskId, "task_id");
        decision = AgentValues.requireDecision(decision);
        matchedPolicyIds = matchedPolicyIds == null ? List.of() : List.copyOf(matchedPolicyIds);
        action = AgentValues.requireTrimmed(action, "action is required");
        resource = AgentValues.copyMap(resource);
        reason = AgentValues.requireTrimmed(reason, "reason is required");
    }
}
