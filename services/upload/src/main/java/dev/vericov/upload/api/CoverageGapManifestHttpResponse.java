package dev.vericov.upload.api;

import dev.vericov.upload.application.CoverageGapManifest;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.List;

public record CoverageGapManifestHttpResponse(
        @JsonbProperty("manifest_version") int manifestVersion,
        @JsonbProperty("generated_at") Instant generatedAt,
        RepositoryInfoHttpResponse repository,
        GapManifestReportHttpResponse report,
        PatchCoverageHttpResponse patch,
        @JsonbProperty("failed_gates") List<CoverageGateEvaluationHttpResponse> failedGates,
        List<CoverageGapManifestEntryHttpResponse> entries,
        boolean truncated) {

    public static CoverageGapManifestHttpResponse from(CoverageGapManifest manifest) {
        return new CoverageGapManifestHttpResponse(
                manifest.manifestVersion(),
                manifest.generatedAt(),
                RepositoryInfoHttpResponse.from(manifest.repository()),
                GapManifestReportHttpResponse.from(manifest),
                PatchCoverageHttpResponse.from(manifest.patch()),
                manifest.failedGates().stream().map(CoverageGateEvaluationHttpResponse::from).toList(),
                manifest.entries().stream().map(CoverageGapManifestEntryHttpResponse::from).toList(),
                manifest.truncated());
    }
}
