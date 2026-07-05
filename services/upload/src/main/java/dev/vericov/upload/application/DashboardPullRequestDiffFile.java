package dev.vericov.upload.application;

public record DashboardPullRequestDiffFile(
        String filePath,
        String changeStatus,
        CoverageMetricDetails patchLine,
        int newlyMissedLineCount,
        int lostCoverageLineCount) {
}
