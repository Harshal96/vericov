package dev.vericov.agent.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentEvent(
        String eventName,
        UUID tenantId,
        UUID repositoryId,
        UUID agentTaskId,
        Map<String, Object> payload,
        Instant createdAt) {
    public AgentEvent {
        eventName = AgentValues.requireTrimmed(eventName, "event_name is required");
        tenantId = AgentValues.requireUuid(tenantId, "tenant_id");
        repositoryId = AgentValues.requireUuid(repositoryId, "repository_id");
        agentTaskId = AgentValues.requireUuid(agentTaskId, "agent_task_id");
        payload = AgentValues.copyMap(payload);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
