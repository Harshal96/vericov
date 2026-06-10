package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageMetricDetails;

public record CoverageMetricHttpResponse(long covered, long total) {
    public static CoverageMetricHttpResponse from(CoverageMetricDetails metric) {
        return new CoverageMetricHttpResponse(metric.covered(), metric.total());
    }
}
