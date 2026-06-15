package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGateDetails;
import java.math.BigDecimal;

public record CoverageGateHttpResponse(
        String metric,
        BigDecimal threshold,
        BigDecimal actual,
        String status,
        boolean blocking) {

    static CoverageGateHttpResponse from(CoverageGateDetails gate) {
        return new CoverageGateHttpResponse(
                gate.metric(),
                gate.threshold(),
                gate.actual(),
                gate.status(),
                gate.blocking());
    }
}
