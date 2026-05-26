package dev.vericov.agent.application;

import dev.vericov.agent.application.port.AgentEventPublisher;
import dev.vericov.agent.application.port.AgentTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AgentControlPlaneService {
    private final AgentTaskRepository repository;
    private final AgentEventPublisher eventPublisher;
    private final Clock clock;

    public AgentControlPlaneService(
            AgentTaskRepository repository,
            AgentEventPublisher eventPublisher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AgentTaskDetails createTask(CreateAgentTaskCommand command) {
        Objects.requireNonNull(command, "command");
        AgentValues.rejectSourceBearingMetadata(command.evidence().metadata());
        enforceDebtPolicy(command);

        Instant now = clock.instant();
        UUID taskId = UUID.randomUUID();
        UUID agentRunId = UUID.randomUUID();
        PolicyDecisionDetails policyDecision = defaultPolicyDecision(command, taskId, now);
        AgentTaskDetails task = new AgentTaskDetails(
                taskId,
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                agentRunId,
                command.taskType(),
                command.mode(),
                "queued",
                command.source(),
                command.target(),
                command.evidence(),
                command.requestedBy(),
                policyDecision,
                payload(command),
                Map.of(),
                now,
                now);
        AgentTaskDetails saved = repository.save(task);
        publish("agent.policy_decision.recorded", saved);
        publish("agent.task.created", saved);
        return saved;
    }

    public AgentTaskDetails getTask(UUID taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new AgentRunnerException("not_found", "Agent task not found"));
    }

    public AgentTaskDetails recordPolicyDecision(RecordPolicyDecisionCommand command) {
        AgentTaskDetails existing = getTask(command.taskId());
        if (!existing.tenantId().equals(command.tenantId()) || !existing.repositoryId().equals(command.repositoryId())) {
            throw new AgentRunnerException("validation_error", "Policy decision does not match agent task scope");
        }
        Instant now = clock.instant();
        PolicyDecisionDetails decision = new PolicyDecisionDetails(
                UUID.randomUUID(),
                command.tenantId(),
                command.taskId(),
                command.repositoryId(),
                command.decision(),
                command.matchedPolicyIds(),
                command.action(),
                command.resource(),
                command.reason(),
                now);
        AgentTaskDetails updated = repository.updatePolicyDecision(command.taskId(), decision, now);
        publish("agent.policy_decision.recorded", updated);
        return updated;
    }

    private void enforceDebtPolicy(CreateAgentTaskCommand command) {
        Object debtStatus = command.evidence().metadata().get("debt_status");
        if ("generate_tests".equals(command.taskType())
                && "active".equals(String.valueOf(debtStatus))
                && !"user".equals(command.requestedBy().type())) {
            throw new AgentRunnerException(
                    "policy_denied",
                    "Active debt-suppressed findings require an explicit user request");
        }
    }

    private PolicyDecisionDetails defaultPolicyDecision(CreateAgentTaskCommand command, UUID taskId, Instant now) {
        String decision = "open_pr".equals(command.mode()) ? "require_approval" : "allow";
        String reason = "open_pr".equals(command.mode())
                ? "Open PR mode requires explicit approval before provider actions"
                : "Metadata-only coverage gap task accepted";
        return new PolicyDecisionDetails(
                UUID.randomUUID(),
                command.tenantId(),
                taskId,
                command.repositoryId(),
                decision,
                List.of(),
                "coverage_gap." + command.taskType(),
                resource(command),
                reason,
                now);
    }

    private void publish(String name, AgentTaskDetails task) {
        eventPublisher.publish(new AgentEvent(
                name,
                task.tenantId(),
                task.repositoryId(),
                task.id(),
                Map.of("agent_run_id", task.agentRunId().toString(), "task_type", task.taskType()),
                clock.instant()));
    }

    private static Map<String, Object> payload(CreateAgentTaskCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source(command.source()));
        payload.put("target", target(command.target()));
        payload.put("evidence", evidence(command.evidence()));
        payload.put("requested_by", Map.of(
                "type", command.requestedBy().type(),
                "id", command.requestedBy().id()));
        return Map.copyOf(payload);
    }

    private static Map<String, Object> source(AgentTaskSource source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", source.type());
        payload.put("coverage_report_id", source.coverageReportId().toString());
        payload.put("coverage_gap_finding_ids", source.coverageGapFindingIds().stream().map(UUID::toString).toList());
        payload.put("commit_sha", source.commitSha());
        if (source.pullRequestNumber() != null) {
            payload.put("pull_request_number", source.pullRequestNumber());
        }
        if (source.baseSha() != null) {
            payload.put("base_sha", source.baseSha());
        }
        if (source.headSha() != null) {
            payload.put("head_sha", source.headSha());
        }
        return Map.copyOf(payload);
    }

    private static Map<String, Object> target(AgentTaskTarget target) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_path", target.filePath());
        payload.put("line_start", target.lineStart());
        payload.put("line_end", target.lineEnd());
        payload.put("risk_level", target.riskLevel());
        payload.put("owners", target.owners());
        if (target.componentId() != null) {
            payload.put("component_id", target.componentId().toString());
        }
        return Map.copyOf(payload);
    }

    private static Map<String, Object> evidence(AgentTaskEvidence evidence) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason_code", evidence.reasonCode());
        payload.put("risk_score", evidence.riskScore());
        payload.put("context_version", evidence.contextVersion());
        payload.put("metadata", evidence.metadata());
        return Map.copyOf(payload);
    }

    private static Map<String, Object> resource(CreateAgentTaskCommand command) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", command.source().type());
        resource.put("coverage_report_id", command.source().coverageReportId().toString());
        resource.put("coverage_gap_finding_ids", command.source().coverageGapFindingIds().stream().map(UUID::toString).toList());
        resource.put("file_path", command.target().filePath());
        resource.put("line_start", command.target().lineStart());
        resource.put("line_end", command.target().lineEnd());
        resource.put("risk_level", command.target().riskLevel());
        return Map.copyOf(resource);
    }
}
