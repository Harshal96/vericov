package dev.vericov.upload.application;

import java.util.List;

public record DashboardFileSummary(
        String filePath,
        String packageName,
        List<String> owners,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement) {

    public DashboardFileSummary {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
