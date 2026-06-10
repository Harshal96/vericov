package dev.vericov.analysis.coverage;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record CoverageAnalysisInput(
        UUID uploadId,
        UUID tenantId,
        UUID repositoryId,
        String provider,
        String commitSha,
        String branch,
        Integer pullRequestNumber,
        List<CoverageInputArtifact> artifacts) {

    public CoverageAnalysisInput {
        provider = provider == null || provider.isBlank() ? "github" : provider.trim().toLowerCase(Locale.ROOT);
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }

    public CoverageAnalysisInput(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            List<CoverageInputArtifact> artifacts) {
        this(uploadId, tenantId, repositoryId, "github", commitSha, branch, pullRequestNumber, artifacts);
    }

    public List<CoverageInputArtifact> coverageArtifacts() {
        return artifacts.stream()
                .filter(CoverageInputArtifact::isCoverageArtifact)
                .toList();
    }

    public List<CoverageInputArtifact> testResultArtifacts() {
        return artifacts.stream()
                .filter(CoverageInputArtifact::isTestResultArtifact)
                .toList();
    }
}
