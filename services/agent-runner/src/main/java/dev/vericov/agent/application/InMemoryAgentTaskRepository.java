package dev.vericov.agent.application;

import dev.vericov.agent.application.port.AgentTaskRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentTaskRepository implements AgentTaskRepository {
    private final Map<UUID, AgentTaskDetails> tasksById = new ConcurrentHashMap<>();

    @Override
    public AgentTaskDetails save(AgentTaskDetails task) {
        tasksById.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<AgentTaskDetails> findById(UUID taskId) {
        return Optional.ofNullable(tasksById.get(taskId));
    }

    @Override
    public AgentTaskDetails updatePolicyDecision(
            UUID taskId,
            PolicyDecisionDetails policyDecision,
            Instant updatedAt) {
        AgentTaskDetails existing = findById(taskId)
                .orElseThrow(() -> new AgentRunnerException("not_found", "Agent task not found"));
        AgentTaskDetails updated = existing.withPolicyDecision(policyDecision, updatedAt);
        tasksById.put(taskId, updated);
        return updated;
    }
}
