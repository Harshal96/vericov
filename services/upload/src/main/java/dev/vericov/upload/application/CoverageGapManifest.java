package dev.vericov.upload.application;

import java.time.Instant;
import java.util.List;

public record CoverageGapManifest(
        int manifestVersion,
        Instant generatedAt,
        RepositoryInfo repository,
        ResolvedCoverageRef resolved,
        Integer pullRequestNumber,
        String gateStatus,
        String configSha256,
        PatchCoverageDetails patch,
        List<CoverageGateEvaluationDetails> failedGates,
        List<CoverageGapManifestEntry> entries,
        boolean truncated) {

    public CoverageGapManifest {
        failedGates = List.copyOf(failedGates == null ? List.of() : failedGates);
        entries = List.copyOf(entries == null ? List.of() : entries);
    }
}
