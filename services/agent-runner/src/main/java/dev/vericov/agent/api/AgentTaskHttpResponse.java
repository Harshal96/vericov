package dev.vericov.agent.api;

import dev.vericov.agent.application.AgentTaskDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentTaskHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("org_id") UUID orgId,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("agent_run_id") UUID agentRunId,
        @JsonbProperty("task_type") String taskType,
        String mode,
        String status,
        AgentTaskSourceHttpResponse source,
        AgentTaskTargetHttpResponse target,
        AgentTaskEvidenceHttpResponse evidence,
        @JsonbProperty("requested_by") RequestedByHttpRequest requestedBy,
        @JsonbProperty("policy_decision") PolicyDecisionHttpResponse policyDecision,
        Map<String, Object> payload,
        @JsonbProperty("created_at") Instant createdAt,
        @JsonbProperty("updated_at") Instant updatedAt) {
    public static AgentTaskHttpResponse from(AgentTaskDetails task) {
        return new AgentTaskHttpResponse(
                task.id(),
                task.tenantId(),
                task.orgId(),
                task.repositoryId(),
                task.agentRunId(),
                task.taskType(),
                task.mode(),
                task.status(),
                AgentTaskSourceHttpResponse.from(task),
                AgentTaskTargetHttpResponse.from(task),
                AgentTaskEvidenceHttpResponse.from(task),
                new RequestedByHttpRequest(task.requestedBy().type(), task.requestedBy().id()),
                PolicyDecisionHttpResponse.from(task),
                task.payload(),
                task.createdAt(),
                task.updatedAt());
    }
}

record AgentTaskSourceHttpResponse(
        String type,
        @JsonbProperty("coverage_report_id") UUID coverageReportId,
        @JsonbProperty("coverage_gap_finding_ids") List<UUID> coverageGapFindingIds,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        @JsonbProperty("commit_sha") String commitSha,
        @JsonbProperty("base_sha") String baseSha,
        @JsonbProperty("head_sha") String headSha) {
    static AgentTaskSourceHttpResponse from(AgentTaskDetails task) {
        return new AgentTaskSourceHttpResponse(
                task.source().type(),
                task.source().coverageReportId(),
                task.source().coverageGapFindingIds(),
                task.source().pullRequestNumber(),
                task.source().commitSha(),
                task.source().baseSha(),
                task.source().headSha());
    }
}

record AgentTaskTargetHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("line_start") int lineStart,
        @JsonbProperty("line_end") int lineEnd,
        @JsonbProperty("risk_level") String riskLevel,
        @JsonbProperty("component_id") UUID componentId,
        List<String> owners) {
    static AgentTaskTargetHttpResponse from(AgentTaskDetails task) {
        return new AgentTaskTargetHttpResponse(
                task.target().filePath(),
                task.target().lineStart(),
                task.target().lineEnd(),
                task.target().riskLevel(),
                task.target().componentId(),
                task.target().owners());
    }
}

record AgentTaskEvidenceHttpResponse(
        @JsonbProperty("reason_code") String reasonCode,
        @JsonbProperty("risk_score") double riskScore,
        @JsonbProperty("context_version") String contextVersion,
        Map<String, Object> metadata) {
    static AgentTaskEvidenceHttpResponse from(AgentTaskDetails task) {
        return new AgentTaskEvidenceHttpResponse(
                task.evidence().reasonCode(),
                task.evidence().riskScore(),
                task.evidence().contextVersion(),
                task.evidence().metadata());
    }
}

record PolicyDecisionHttpResponse(
        UUID id,
        String decision,
        @JsonbProperty("matched_policy_ids") List<UUID> matchedPolicyIds,
        String action,
        Map<String, Object> resource,
        String reason,
        @JsonbProperty("created_at") Instant createdAt) {
    static PolicyDecisionHttpResponse from(AgentTaskDetails task) {
        return new PolicyDecisionHttpResponse(
                task.policyDecision().id(),
                task.policyDecision().decision(),
                task.policyDecision().matchedPolicyIds(),
                task.policyDecision().action(),
                task.policyDecision().resource(),
                task.policyDecision().reason(),
                task.policyDecision().createdAt());
    }
}
