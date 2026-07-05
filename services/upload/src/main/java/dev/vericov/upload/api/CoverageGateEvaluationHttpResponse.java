package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGateEvaluationDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.math.BigDecimal;
import java.util.List;

public record CoverageGateEvaluationHttpResponse(
        @JsonbProperty("gate_name") String gateName,
        @JsonbProperty("gate_type") String gateType,
        String metric,
        @JsonbProperty("scope_type") String scopeType,
        @JsonbProperty("scope_key") String scopeKey,
        @JsonbProperty("scope_path") List<String> scopePath,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking) {

    public static CoverageGateEvaluationHttpResponse from(CoverageGateEvaluationDetails gate) {
        return new CoverageGateEvaluationHttpResponse(
                gate.gateName(),
                gate.gateType(),
                gate.metric(),
                gate.scopeType(),
                gate.scopeKey(),
                gate.scopePath(),
                gate.threshold(),
                gate.actual(),
                gate.status(),
                gate.blocking());
    }
}
