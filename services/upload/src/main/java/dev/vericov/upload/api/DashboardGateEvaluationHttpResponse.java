package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardGateEvaluation;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardGateEvaluationHttpResponse(
        UUID id,
        @JsonbProperty("gate_name") String gateName,
        @JsonbProperty("gate_type") String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking,
        @JsonbProperty("commit_sha") String commitSha,
        String branch,
        @JsonbProperty("pull_request_number") Integer pullRequestNumber,
        @JsonbProperty("evaluated_at") Instant evaluatedAt,
        @JsonbProperty("coverage_report_id") UUID coverageReportId) {

    public static DashboardGateEvaluationHttpResponse from(DashboardGateEvaluation evaluation) {
        return new DashboardGateEvaluationHttpResponse(
                evaluation.id(),
                evaluation.gateName(),
                evaluation.gateType(),
                evaluation.metric(),
                evaluation.threshold(),
                evaluation.actual(),
                evaluation.status(),
                evaluation.blocking(),
                evaluation.commitSha(),
                evaluation.branch(),
                evaluation.pullRequestNumber(),
                evaluation.evaluatedAt(),
                evaluation.coverageReportId());
    }
}
