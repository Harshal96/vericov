package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.DiffCoverageLineDetails;
import jakarta.json.bind.annotation.JsonbProperty;

public record DiffCoverageLineHttpResponse(
        @JsonbProperty("file_path")
        String filePath,
        @JsonbProperty("old_file_path")
        String oldFilePath,
        @JsonbProperty("base_line_number")
        Integer baseLineNumber,
        @JsonbProperty("head_line_number")
        Integer headLineNumber,
        @JsonbProperty("change_type")
        String changeType,
        boolean executable,
        @JsonbProperty("base_hits")
        Long baseHits,
        @JsonbProperty("head_hits")
        Long headHits,
        @JsonbProperty("newly_missed")
        boolean newlyMissed,
        @JsonbProperty("lost_coverage")
        boolean lostCoverage) {

    public static DiffCoverageLineHttpResponse from(DiffCoverageLineDetails details) {
        return new DiffCoverageLineHttpResponse(
                details.filePath(),
                details.oldFilePath(),
                details.baseLineNumber(),
                details.headLineNumber(),
                details.changeType(),
                details.executable(),
                details.baseHits(),
                details.headHits(),
                details.newlyMissed(),
                details.lostCoverage());
    }
}
