package dev.vericov.upload.application;

import java.math.BigDecimal;
import java.util.List;

public record CoverageGateEvaluationDetails(
        String gateName,
        String gateType,
        String metric,
        String scopeType,
        String scopeKey,
        List<String> scopePath,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking) {

    public CoverageGateEvaluationDetails {
        scopePath = List.copyOf(scopePath == null ? List.of() : scopePath);
    }
}
