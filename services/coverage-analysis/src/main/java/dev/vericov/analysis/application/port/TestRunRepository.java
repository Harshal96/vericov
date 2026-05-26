package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.testresults.TestRun;
import java.time.Instant;
import java.util.List;

public interface TestRunRepository {
    void save(CoverageAnalysisInput input, List<TestRun> runs, Instant completedAt);

    static TestRunRepository noop() {
        return (input, runs, completedAt) -> {
        };
    }
}
