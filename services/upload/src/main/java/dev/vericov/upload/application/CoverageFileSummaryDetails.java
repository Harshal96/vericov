package dev.vericov.upload.application;

import java.util.List;

public record CoverageFileSummaryDetails(
        String filePath,
        String leafComponentKey,
        List<String> owners,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement) {

    public CoverageFileSummaryDetails {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }
}
