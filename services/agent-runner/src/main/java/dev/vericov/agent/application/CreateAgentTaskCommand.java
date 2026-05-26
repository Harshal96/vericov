package dev.vericov.agent.application;

import java.util.Objects;
import java.util.UUID;

public record CreateAgentTaskCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String taskType,
        String mode,
        AgentTaskSource source,
        AgentTaskTarget target,
        AgentTaskEvidence evidence,
        RequestedBy requestedBy) {
    public CreateAgentTaskCommand {
        tenantId = AgentValues.requireUuid(tenantId, "tenant_id");
        orgId = AgentValues.requireUuid(orgId, "org_id");
        repositoryId = AgentValues.requireUuid(repositoryId, "repository_id");
        taskType = AgentValues.requireTaskType(taskType);
        mode = AgentValues.requireMode(mode);
        source = Objects.requireNonNull(source, "source is required");
        target = Objects.requireNonNull(target, "target is required");
        evidence = Objects.requireNonNull(evidence, "evidence is required");
        requestedBy = Objects.requireNonNull(requestedBy, "requested_by is required");
    }
}
