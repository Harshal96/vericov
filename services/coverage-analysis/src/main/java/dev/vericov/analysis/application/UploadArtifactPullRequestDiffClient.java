package dev.vericov.analysis.application;

import dev.vericov.analysis.application.port.ArtifactContentStore;
import dev.vericov.analysis.application.port.PullRequestDiffClient;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.diff.PullRequestDiff;
import dev.vericov.analysis.diff.UnifiedDiffParser;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import java.util.Objects;

/**
 * Resolves the pull request diff from the {@code diff} upload artifact
 * persisted by the Upload Service, rather than any Git provider. The Upload
 * Service already validated the artifact at submission time, so a parse
 * failure here indicates corruption and is treated as non-retryable.
 */
public class UploadArtifactPullRequestDiffClient implements PullRequestDiffClient {
    private final ArtifactContentStore contentStore;
    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    public UploadArtifactPullRequestDiffClient(ArtifactContentStore contentStore) {
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    }

    @Override
    public PullRequestDiff fetch(CoverageAnalysisInput input, CoverageReport headReport) {
        CoverageInputArtifact diffArtifact = input.diffArtifact()
                .orElseThrow(() -> new NonRetryableAnalysisException(
                        "No diff artifact present for upload " + input.uploadId()));
        if (input.baseSha() == null || input.baseSha().isBlank()) {
            throw new NonRetryableAnalysisException(
                    "Upload " + input.uploadId() + " has a diff artifact but no base_sha");
        }
        byte[] content;
        try {
            content = contentStore.read(diffArtifact.storageBucket(), diffArtifact.storagePath());
        } catch (RuntimeException exception) {
            throw new NonRetryableAnalysisException(
                    "Failed to read diff artifact for upload " + input.uploadId(), exception);
        }
        if (content == null || content.length == 0) {
            throw new NonRetryableAnalysisException(
                    "Diff artifact bytes missing for upload " + input.uploadId());
        }
        try {
            return parser.parse(input.baseSha(), input.commitSha(), content);
        } catch (RuntimeException exception) {
            throw new NonRetryableAnalysisException(
                    "Diff artifact for upload " + input.uploadId() + " no longer parses as a unified diff",
                    exception);
        }
    }
}
