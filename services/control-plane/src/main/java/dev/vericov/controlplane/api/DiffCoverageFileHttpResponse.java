package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.DiffCoverageFileDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record DiffCoverageFileHttpResponse(
        @JsonbProperty("file_path")
        String filePath,
        @JsonbProperty("old_file_path")
        String oldFilePath,
        @JsonbProperty("change_status")
        String changeStatus,
        @JsonbProperty("patch_line")
        CoverageMetricHttpResponse patchLine,
        @JsonbProperty("newly_missed_line_count")
        int newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count")
        int lostCoverageLineCount,
        List<DiffCoverageLineHttpResponse> lines) {

    public static DiffCoverageFileHttpResponse from(DiffCoverageFileDetails details) {
        return new DiffCoverageFileHttpResponse(
                details.filePath(),
                details.oldFilePath(),
                details.changeStatus(),
                CoverageMetricHttpResponse.from(details.patchLine()),
                details.newlyMissedLineCount(),
                details.lostCoverageLineCount(),
                details.lines().stream().map(DiffCoverageLineHttpResponse::from).toList());
    }
}
