package dev.vericov.upload.api;

import dev.vericov.upload.application.DashboardPullRequestDiffFile;
import jakarta.json.bind.annotation.JsonbProperty;

public record DashboardPullRequestDiffFileHttpResponse(
        @JsonbProperty("file_path") String filePath,
        @JsonbProperty("change_status") String changeStatus,
        @JsonbProperty("patch_line_covered") long patchLineCovered,
        @JsonbProperty("patch_line_total") long patchLineTotal,
        @JsonbProperty("newly_missed_line_count") int newlyMissedLineCount,
        @JsonbProperty("lost_coverage_line_count") int lostCoverageLineCount) {
    public static DashboardPullRequestDiffFileHttpResponse from(DashboardPullRequestDiffFile file) {
        return new DashboardPullRequestDiffFileHttpResponse(
                file.filePath(),
                file.changeStatus(),
                file.patchLine().covered(),
                file.patchLine().total(),
                file.newlyMissedLineCount(),
                file.lostCoverageLineCount());
    }
}
