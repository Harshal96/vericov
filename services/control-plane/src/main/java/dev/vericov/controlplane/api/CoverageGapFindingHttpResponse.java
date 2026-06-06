package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageGapFindingDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CoverageGapFindingHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("org_id")
        UUID organizationId,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("coverage_report_id")
        UUID coverageReportId,
        @JsonbProperty("pr_diff_id")
        UUID pullRequestDiffId,
        @JsonbProperty("component_id")
        UUID componentId,
        @JsonbProperty("commit_sha")
        String commitSha,
        @JsonbProperty("pull_request_number")
        Integer pullRequestNumber,
        @JsonbProperty("file_path")
        String filePath,
        @JsonbProperty("target_type")
        String targetType,
        @JsonbProperty("line_start")
        Integer lineStart,
        @JsonbProperty("line_end")
        Integer lineEnd,
        @JsonbProperty("symbol_name")
        String symbolName,
        @JsonbProperty("reason_code")
        String reasonCode,
        String explanation,
        String confidence,
        @JsonbProperty("risk_score")
        BigDecimal riskScore,
        @JsonbProperty("risk_level")
        String riskLevel,
        List<String> owners,
        @JsonbProperty("next_action")
        String nextAction,
        String status,
        @JsonbProperty("evidence_json")
        Map<String, Object> evidence,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static CoverageGapFindingHttpResponse from(CoverageGapFindingDetails details) {
        return new CoverageGapFindingHttpResponse(
                details.id(),
                details.tenantId(),
                details.organizationId(),
                details.repositoryId(),
                details.coverageReportId(),
                details.pullRequestDiffId(),
                details.componentId(),
                details.commitSha(),
                details.pullRequestNumber(),
                details.filePath(),
                details.targetType(),
                details.lineStart(),
                details.lineEnd(),
                details.symbolName(),
                details.reasonCode(),
                details.explanation(),
                details.confidence(),
                details.riskScore(),
                details.riskLevel(),
                details.owners(),
                details.nextAction(),
                details.status(),
                details.evidence(),
                details.createdAt(),
                details.updatedAt());
    }
}
