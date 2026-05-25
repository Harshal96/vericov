package dev.vericov.analysis.gates;

import dev.vericov.analysis.coverage.CoverageMetric;
import java.util.Objects;

public record ComponentRollup(
        String componentId,
        CoverageMetric line,
        CoverageMetric branch,
        CoverageMetric function,
        CoverageMetric statement) {

    public ComponentRollup {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(statement, "statement");
    }
}
