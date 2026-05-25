package dev.vericov.analysis.coverage;

public record CoverageFileSummary(
        String filePath,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement) {
}
