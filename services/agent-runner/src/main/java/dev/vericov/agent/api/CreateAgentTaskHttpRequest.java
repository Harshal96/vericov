package dev.vericov.agent.api;

import dev.vericov.agent.application.AgentTaskEvidence;
import dev.vericov.agent.application.AgentTaskSource;
import dev.vericov.agent.application.AgentTaskTarget;
import dev.vericov.agent.application.AgentRunnerException;
import dev.vericov.agent.application.CreateAgentTaskCommand;
import dev.vericov.agent.application.RequestedBy;
import jakarta.json.bind.annotation.JsonbProperty;

public record CreateAgentTaskHttpRequest(
        @JsonbProperty("tenant_id") String tenantId,
        @JsonbProperty("org_id") String orgId,
        @JsonbProperty("repository_id") String repositoryId,
        @JsonbProperty("task_type") String taskType,
        String mode,
        AgentTaskSourceHttpRequest source,
        AgentTaskTargetHttpRequest target,
        AgentTaskEvidenceHttpRequest evidence,
        @JsonbProperty("requested_by") RequestedByHttpRequest requestedBy) {
    public CreateAgentTaskCommand toCommand() {
        return new CreateAgentTaskCommand(
                ApiParsing.parseRequiredUuid(tenantId, "tenant_id"),
                ApiParsing.parseRequiredUuid(orgId, "org_id"),
                ApiParsing.parseRequiredUuid(repositoryId, "repository_id"),
                taskType,
                mode,
                new AgentTaskSource(
                        requirePart(source, "source").type(),
                        ApiParsing.parseRequiredUuid(source.coverageReportId(), "source.coverage_report_id"),
                        ApiParsing.parseUuidList(source.coverageGapFindingIds(), "source.coverage_gap_finding_ids"),
                        source.pullRequestNumber(),
                        source.commitSha(),
                        source.baseSha(),
                        source.headSha()),
                new AgentTaskTarget(
                        requirePart(target, "target").filePath(),
                        target.lineStart(),
                        target.lineEnd(),
                        target.riskLevel(),
                        ApiParsing.parseOptionalUuid(target.componentId(), "target.component_id"),
                        target.owners()),
                new AgentTaskEvidence(
                        requirePart(evidence, "evidence").reasonCode(),
                        evidence.riskScore(),
                        evidence.contextVersion(),
                        evidence.metadata()),
                new RequestedBy(
                        requirePart(requestedBy, "requested_by").type(),
                        requestedBy.id()));
    }

    public CreateAgentTaskHttpRequest withTenantId(String nextTenantId) {
        return new CreateAgentTaskHttpRequest(
                nextTenantId,
                orgId,
                repositoryId,
                taskType,
                mode,
                source,
                target,
                evidence,
                requestedBy);
    }

    public CreateAgentTaskHttpRequest withSource(AgentTaskSourceHttpRequest nextSource) {
        return new CreateAgentTaskHttpRequest(
                tenantId,
                orgId,
                repositoryId,
                taskType,
                mode,
                nextSource,
                target,
                evidence,
                requestedBy);
    }

    private static <T> T requirePart(T value, String fieldName) {
        if (value == null) {
            throw new AgentRunnerException("validation_error", fieldName + " is required");
        }
        return value;
    }
}
