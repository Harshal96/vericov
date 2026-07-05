package dev.vericov.upload.application;

import java.util.List;

public record CoverageFileDetail(
        String filePath,
        String leafComponentKey,
        List<String> owners,
        CoverageMetricDetails line,
        CoverageMetricDetails branchCoverage,
        CoverageMetricDetails function,
        CoverageMetricDetails statement,
        List<CoverageLineRange> uncoveredRanges) {

    public CoverageFileDetail {
        owners = List.copyOf(owners == null ? List.of() : owners);
        uncoveredRanges = List.copyOf(uncoveredRanges == null ? List.of() : uncoveredRanges);
    }
}
