package dev.vericov.agent.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AgentTaskDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        UUID agentRunId,
        String taskType,
        String mode,
        String status,
        AgentTaskSource source,
        AgentTaskTarget target,
        AgentTaskEvidence evidence,
        RequestedBy requestedBy,
        PolicyDecisionDetails policyDecision,
        Map<String, Object> payload,
        Map<String, Object> result,
        Instant createdAt,
        Instant updatedAt) {
    public AgentTaskDetails {
        id = AgentValues.requireUuid(id, "agent_task.id");
        tenantId = AgentValues.requireUuid(tenantId, "agent_task.tenant_id");
        orgId = AgentValues.requireUuid(orgId, "agent_task.org_id");
        repositoryId = AgentValues.requireUuid(repositoryId, "agent_task.repository_id");
        agentRunId = AgentValues.requireUuid(agentRunId, "agent_task.agent_run_id");
        taskType = AgentValues.requireTaskType(taskType);
        mode = AgentValues.requireMode(mode);
        status = AgentValues.requireCanonical(status, "agent_task.status is required");
        source = Objects.requireNonNull(source, "source is required");
        target = Objects.requireNonNull(target, "target is required");
        evidence = Objects.requireNonNull(evidence, "evidence is required");
        requestedBy = Objects.requireNonNull(requestedBy, "requested_by is required");
        policyDecision = Objects.requireNonNull(policyDecision, "policy_decision is required");
        payload = AgentValues.copyMap(payload);
        result = AgentValues.copyMap(result);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public AgentTaskDetails withPolicyDecision(PolicyDecisionDetails nextPolicyDecision, Instant updatedAt) {
        return new AgentTaskDetails(
                id,
                tenantId,
                orgId,
                repositoryId,
                agentRunId,
                taskType,
                mode,
                status,
                source,
                target,
                evidence,
                requestedBy,
                nextPolicyDecision,
                payload,
                result,
                createdAt,
                updatedAt);
    }
}
