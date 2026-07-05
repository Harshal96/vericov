package dev.vericov.upload.api;

import dev.vericov.upload.application.PatchCoverageFileDetails;
import jakarta.json.bind.annotation.JsonbProperty;

public record PatchCoverageFileHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("old_file_path") String oldFilePath,
        @JsonbProperty("change_status") String changeStatus,
        @JsonbProperty("line_covered") long lineCovered,
        @JsonbProperty("line_total") long lineTotal,
        @JsonbProperty("newly_missed_line_count") long newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count") long lostCoverageLineCount) {

    public static PatchCoverageFileHttpResponse from(PatchCoverageFileDetails file) {
        return new PatchCoverageFileHttpResponse(
                file.filePath(),
                file.oldFilePath(),
                file.changeStatus(),
                file.lineCovered(),
                file.lineTotal(),
                file.newlyMissedLineCount(),
                file.lostCoverageLineCount());
    }
}
