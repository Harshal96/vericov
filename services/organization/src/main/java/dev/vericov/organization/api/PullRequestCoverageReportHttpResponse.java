package dev.vericov.organization.api;

import dev.vericov.organization.application.PullRequestCoverageReportDetails;
import jakarta.json.bind.annotation.JsonbProperty;

public record PullRequestCoverageReportHttpResponse(
        @JsonbProperty("pull_request_number")
        int pullRequestNumber,
        @JsonbProperty("head_sha")
        String headSha,
        CoverageReportHttpResponse report,
        PullRequestDiffCoverageHttpResponse diff) {

    public static PullRequestCoverageReportHttpResponse from(PullRequestCoverageReportDetails details) {
        return new PullRequestCoverageReportHttpResponse(
                details.pullRequestNumber(),
                details.headSha(),
                CoverageReportHttpResponse.from(details.report()),
                details.diffCoverage() == null ? null : PullRequestDiffCoverageHttpResponse.from(details.diffCoverage()));
    }
}
