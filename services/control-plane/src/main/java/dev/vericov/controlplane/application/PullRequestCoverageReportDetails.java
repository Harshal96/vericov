package dev.vericov.controlplane.application;

public record PullRequestCoverageReportDetails(
        int pullRequestNumber,
        String headSha,
        CoverageReportDetails report,
        PullRequestDiffCoverageDetails diffCoverage) {

    public PullRequestCoverageReportDetails(
            int pullRequestNumber,
            String headSha,
            CoverageReportDetails report) {
        this(pullRequestNumber, headSha, report, null);
    }
}
