package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardGapFinding;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardGapFindingHttpResponse(
        UUID id,
        @JsonbProperty("coverage_report_id") UUID coverageReportId,
        @JsonbProperty("repository_id") UUID repositoryId,
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("target_type") String targetType,
        @JsonbProperty("line_start") Integer lineStart,
        @JsonbProperty("line_end") Integer lineEnd,
        @JsonbProperty("symbol_name") String symbolName,
        @JsonbProperty("reason_code") String reasonCode,
        String explanation,
        String confidence,
        @JsonbProperty("risk_score") BigDecimal riskScore,
        @JsonbProperty("risk_level") String riskLevel,
        List<String> owners,
        @JsonbProperty("next_action") String nextAction,
        String status,
        @JsonbProperty("commit_sha") String commitSha,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber) {

    public static DashboardGapFindingHttpResponse from(DashboardGapFinding gap) {
        return new DashboardGapFindingHttpResponse(
                gap.id(),
                gap.coverageReportId(),
                gap.repositoryId(),
                gap.filePath(),
                gap.targetType(),
                gap.lineStart(),
                gap.lineEnd(),
                gap.symbolName(),
                gap.reasonCode(),
                gap.explanation(),
                gap.confidence(),
                gap.riskScore(),
                gap.riskLevel(),
                gap.owners(),
                gap.nextAction(),
                gap.status(),
                gap.commitSha(),
                gap.pullRequestNumber());
    }
}
