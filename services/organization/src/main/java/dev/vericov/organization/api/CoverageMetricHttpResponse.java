package dev.vericov.organization.api;

import dev.vericov.organization.application.CoverageMetricDetails;
import java.math.BigDecimal;

public record CoverageMetricHttpResponse(
        int covered,
        int total,
        BigDecimal percent) {

    public static CoverageMetricHttpResponse from(CoverageMetricDetails details) {
        return new CoverageMetricHttpResponse(details.covered(), details.total(), details.percent());
    }
}
