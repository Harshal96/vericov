package dev.vericov.analysis.coverage;

import java.util.List;
import java.util.UUID;

public record CoverageFileSummary(
        String filePath,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement,
        UUID componentId,
        String packageName,
        List<String> owners) {

    public CoverageFileSummary {
        owners = List.copyOf(owners == null ? List.of() : owners);
    }

    public CoverageFileSummary(
            String filePath,
            CoverageMetric line,
            CoverageMetric branch,
            CoverageMetric function,
            CoverageMetric statement) {
        this(filePath, line, branch, function, statement, null, null, List.of());
    }
}
