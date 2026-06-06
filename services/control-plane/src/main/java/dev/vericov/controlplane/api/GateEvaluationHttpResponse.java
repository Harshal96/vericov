package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.GateEvaluationDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GateEvaluationHttpResponse(
        UUID id,
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("coverage_report_id")
        UUID coverageReportId,
        @JsonbProperty("commit_sha")
        String commitSha,
        String branch,
        @JsonbProperty("pull_request_number")
        Integer pullRequestNumber,
        @JsonbProperty("gate_name")
        String gateName,
        @JsonbProperty("gate_type")
        String gateType,
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking,
        Map<String, Object> details,
        @JsonbProperty("evaluated_at")
        Instant evaluatedAt) {

    public static GateEvaluationHttpResponse from(GateEvaluationDetails details) {
        return new GateEvaluationHttpResponse(
                details.id(),
                details.repositoryId(),
                details.coverageReportId(),
                details.commitSha(),
                details.branch(),
                details.pullRequestNumber(),
                details.gateName(),
                details.gateType(),
                details.metric(),
                details.threshold(),
                details.actual(),
                details.status(),
                details.blocking(),
                details.details(),
                details.evaluatedAt());
    }
}
