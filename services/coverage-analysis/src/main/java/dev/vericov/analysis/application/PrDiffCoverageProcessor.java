package dev.vericov.analysis.application;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageReport;

public interface PrDiffCoverageProcessor {
    void process(CoverageAnalysisInput input, CoverageReport headReport);

    static PrDiffCoverageProcessor noop() {
        return (input, headReport) -> {
        };
    }
}
