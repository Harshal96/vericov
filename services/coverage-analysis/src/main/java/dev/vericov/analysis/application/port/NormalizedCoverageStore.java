package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageReport;

public interface NormalizedCoverageStore {
    NormalizedCoverageLocation store(CoverageReport report);
}
