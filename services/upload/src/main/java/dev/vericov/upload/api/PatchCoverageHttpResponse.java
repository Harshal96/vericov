package dev.vericov.upload.api;

import dev.vericov.upload.application.PatchCoverageDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record PatchCoverageHttpResponse(
        String status,
        @JsonbProperty("base_sha") String baseSha,
        @JsonbProperty("head_sha") String headSha,
        @JsonbProperty("line_covered") long lineCovered,
        @JsonbProperty("line_total") long lineTotal,
        @JsonbProperty("newly_missed_line_count") long newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count") long lostCoverageLineCount,
        List<PatchCoverageFileHttpResponse> files) {

    public static PatchCoverageHttpResponse from(PatchCoverageDetails patch) {
        if (patch == null) {
            return null;
        }
        return new PatchCoverageHttpResponse(
                patch.status(),
                patch.baseSha(),
                patch.headSha(),
                patch.lineCovered(),
                patch.lineTotal(),
                patch.newlyMissedLineCount(),
                patch.lostCoverageLineCount(),
                patch.files().stream().map(PatchCoverageFileHttpResponse::from).toList());
    }
}
