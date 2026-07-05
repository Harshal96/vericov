package dev.vericov.analysis.application;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.DiffCoverageReport;
import java.util.Objects;

/**
 * Runs diff coverage only when the upload actually carries a pull request
 * number and a {@code diff} artifact. Preserves the exact no-op behavior of
 * every existing client for uploads with a pull request number but no diff.
 */
public class ArtifactAwarePrDiffCoverageProcessor implements PrDiffCoverageProcessor {
    private final PrDiffCoverageProcessor delegate;

    public ArtifactAwarePrDiffCoverageProcessor(PrDiffCoverageProcessor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public DiffCoverageReport process(CoverageAnalysisInput input, CoverageReport headReport) {
        if (input.pullRequestNumber() == null) {
            return null;
        }
        if (input.diffArtifact().isEmpty()) {
            return null;
        }
        return delegate.process(input, headReport);
    }
}
