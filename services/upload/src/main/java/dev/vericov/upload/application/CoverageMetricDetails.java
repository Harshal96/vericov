package dev.vericov.upload.application;

public record CoverageMetricDetails(long covered, long total) {
    public CoverageMetricDetails {
        if (covered < 0 || total < 0 || covered > total) {
            throw new IllegalArgumentException("coverage metric values are invalid");
        }
    }
}
