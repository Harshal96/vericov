package dev.vericov.agent.application.port;

import dev.vericov.agent.application.AgentTaskDetails;
import dev.vericov.agent.application.PolicyDecisionDetails;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AgentTaskRepository {
    AgentTaskDetails save(AgentTaskDetails task);

    Optional<AgentTaskDetails> findById(UUID taskId);

    AgentTaskDetails updatePolicyDecision(UUID taskId, PolicyDecisionDetails policyDecision, Instant updatedAt);
}
