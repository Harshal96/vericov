package dev.vericov.analysis.application.port;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.PullRequestDiff;

public interface PullRequestDiffClient {
    PullRequestDiff fetch(CoverageAnalysisInput input, CoverageReport headReport);
}
