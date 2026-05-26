package dev.vericov.agent.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PolicyDecisionDetails(
        UUID id,
        UUID tenantId,
        UUID agentTaskId,
        UUID repositoryId,
        String decision,
        List<UUID> matchedPolicyIds,
        String action,
        Map<String, Object> resource,
        String reason,
        Instant createdAt) {
    public PolicyDecisionDetails {
        id = AgentValues.requireUuid(id, "policy_decision.id");
        tenantId = AgentValues.requireUuid(tenantId, "policy_decision.tenant_id");
        agentTaskId = AgentValues.requireUuid(agentTaskId, "policy_decision.agent_task_id");
        repositoryId = AgentValues.requireUuid(repositoryId, "policy_decision.repository_id");
        decision = AgentValues.requireDecision(decision);
        matchedPolicyIds = matchedPolicyIds == null ? List.of() : List.copyOf(matchedPolicyIds);
        action = AgentValues.requireTrimmed(action, "policy_decision.action is required");
        resource = AgentValues.copyMap(resource);
        reason = AgentValues.requireTrimmed(reason, "policy_decision.reason is required");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
