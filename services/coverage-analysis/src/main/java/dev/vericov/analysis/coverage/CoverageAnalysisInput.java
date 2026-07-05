package dev.vericov.analysis.coverage;

import dev.vericov.componentconfig.ComponentConfigJson;
import dev.vericov.componentconfig.ComponentConfigSnapshot;
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
        String baseSha,
        List<String> ignore,
        String configSnapshotJson,
        String configSha256,
        List<CoverageInputArtifact> artifacts) {

    public CoverageAnalysisInput {
        provider = provider == null || provider.isBlank() ? "github" : provider.trim().toLowerCase(Locale.ROOT);
        ignore = List.copyOf(ignore == null ? List.of() : ignore);
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
    }

    public CoverageAnalysisInput(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String provider,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            List<String> ignore,
            String configSnapshotJson,
            String configSha256,
            List<CoverageInputArtifact> artifacts) {
        this(
                uploadId,
                tenantId,
                repositoryId,
                provider,
                commitSha,
                branch,
                pullRequestNumber,
                null,
                ignore,
                configSnapshotJson,
                configSha256,
                artifacts);
    }

    public CoverageAnalysisInput(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String provider,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            List<String> ignore,
            List<CoverageInputArtifact> artifacts) {
        this(
                uploadId,
                tenantId,
                repositoryId,
                provider,
                commitSha,
                branch,
                pullRequestNumber,
                null,
                ignore,
                null,
                null,
                artifacts);
    }

    public CoverageAnalysisInput(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String provider,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            List<CoverageInputArtifact> artifacts) {
        this(uploadId, tenantId, repositoryId, provider, commitSha, branch, pullRequestNumber, null, List.of(), null, null, artifacts);
    }

    public CoverageAnalysisInput(
            UUID uploadId,
            UUID tenantId,
            UUID repositoryId,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            List<CoverageInputArtifact> artifacts) {
        this(uploadId, tenantId, repositoryId, "github", commitSha, branch, pullRequestNumber, null, List.of(), null, null, artifacts);
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

    public java.util.Optional<CoverageInputArtifact> diffArtifact() {
        return artifacts.stream()
                .filter(CoverageInputArtifact::isDiffArtifact)
                .findFirst();
    }

    public ComponentConfigSnapshot configSnapshot() {
        return configSnapshotJson == null ? null : ComponentConfigJson.parse(configSnapshotJson);
    }
}
