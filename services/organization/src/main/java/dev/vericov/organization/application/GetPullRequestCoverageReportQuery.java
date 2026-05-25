package dev.vericov.organization.application;

import java.util.UUID;

public record GetPullRequestCoverageReportQuery(
        UUID requesterUserId,
        UUID organizationId,
        UUID repositoryId,
        int pullRequestNumber,
        boolean includeFiles,
        int fileLimit,
        boolean includeDiffLines) {

    public GetPullRequestCoverageReportQuery(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId,
            int pullRequestNumber,
            boolean includeFiles,
            int fileLimit) {
        this(requesterUserId, organizationId, repositoryId, pullRequestNumber, includeFiles, fileLimit, false);
    }
}
