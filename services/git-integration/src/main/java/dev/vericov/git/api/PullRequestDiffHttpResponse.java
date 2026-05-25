package dev.vericov.git.api;

import dev.vericov.git.application.GitPullRequestDiffDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;
import java.util.UUID;

public record PullRequestDiffHttpResponse(
        @JsonbProperty("repository_id")
        UUID repositoryId,
        @JsonbProperty("pull_request_number")
        int pullRequestNumber,
        @JsonbProperty("base_sha")
        String baseSha,
        @JsonbProperty("head_sha")
        String headSha,
        List<DiffFileHttpResponse> files) {

    public static PullRequestDiffHttpResponse from(GitPullRequestDiffDetails details) {
        return new PullRequestDiffHttpResponse(
                details.repositoryId(),
                details.pullRequestNumber(),
                details.baseSha(),
                details.headSha(),
                details.files().stream().map(DiffFileHttpResponse::from).toList());
    }
}
