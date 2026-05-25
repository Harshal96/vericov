package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import java.util.UUID;

public interface CoverageAnalysisInputRepository {
    CoverageAnalysisInput load(UUID uploadId);
}
