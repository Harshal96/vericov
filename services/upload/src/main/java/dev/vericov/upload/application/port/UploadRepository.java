package dev.vericov.upload.application.port;

import dev.vericov.upload.application.CoverageFileDetail;
import dev.vericov.upload.application.CoverageFileSummaryDetails;
import dev.vericov.upload.application.CoverageGapFindingDetails;
import dev.vericov.upload.application.CoverageGapManifestEntry;
import dev.vericov.upload.application.CoverageGateEvaluationDetails;
import dev.vericov.upload.application.PatchCoverageDetails;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.RepositoryInfo;
import dev.vericov.upload.application.ResolvedCoverageRef;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.CoverageReportDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadRepository {
    Optional<QueuedUpload> findById(UUID uploadId);

    Optional<QueuedUpload> findByIdempotencyKey(UUID repositoryId, String idempotencyKey);

    void save(QueuedUpload upload, List<StoredArtifact> artifacts);

    List<StoredArtifact> artifactsFor(UUID uploadId);

    default Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
        return Optional.empty();
    }

    /**
     * Resolves a {@code ref} (full commit SHA, branch name, or the
     * repository's default branch when {@code ref} is blank) to the latest
     * {@code complete} coverage report for that repository.
     */
    default Optional<ResolvedCoverageRef> resolveCoverageRef(UUID repositoryId, String ref) {
        return Optional.empty();
    }

    default List<CoverageFileSummaryDetails> filesFor(
            UUID reportId,
            String pathPrefix,
            String componentKey,
            String sort,
            int limit,
            int offset) {
        return List.of();
    }

    default Optional<CoverageFileDetail> fileDetail(UUID reportId, String path) {
        return Optional.empty();
    }

    default List<String> similarFilePaths(UUID reportId, String basename, int max) {
        return List.of();
    }

    default List<CoverageGapFindingDetails> gapsFor(
            UUID reportId,
            String componentKey,
            String minRiskLevel,
            String status,
            int limit,
            int offset) {
        return List.of();
    }

    default List<CoverageGateEvaluationDetails> gatesFor(UUID reportId) {
        return List.of();
    }

    default Optional<PatchCoverageDetails> patchForPullRequest(UUID repositoryId, int pullRequestNumber) {
        return Optional.empty();
    }

    default Optional<RepositoryInfo> repositoryInfo(UUID repositoryId) {
        return Optional.empty();
    }

    /**
     * Resolves the latest diff-bearing coverage report for a pull request
     * number, for manifest assembly (as opposed to {@link #patchForPullRequest}
     * which returns only the patch coverage totals).
     */
    default Optional<ResolvedCoverageRef> resolveCoverageRefForPullRequest(UUID repositoryId, int pullRequestNumber) {
        return Optional.empty();
    }

    /**
     * Assembles gap manifest entries for a report: risk factors parsed from
     * {@code evidence_json}, uncovered line ranges resolved from
     * {@code coverage_line_hits}, and {@code in_patch} determined from the
     * report's diff files, if any.
     */
    default List<CoverageGapManifestEntry> gapManifestEntries(
            UUID reportId,
            String nextAction,
            String minRiskLevel,
            int limit,
            int offset) {
        return List.of();
    }
}
