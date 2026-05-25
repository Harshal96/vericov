package dev.vericov.analysis.application;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageReport;

public interface PrDiffCoverageProcessor {
    DiffCoverageReport process(CoverageAnalysisInput input, CoverageReport headReport);

    static PrDiffCoverageProcessor noop() {
        return (input, headReport) -> null;
    }
}
