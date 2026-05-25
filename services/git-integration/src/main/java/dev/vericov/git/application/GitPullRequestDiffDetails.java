package dev.vericov.git.application;

import java.util.List;
import java.util.UUID;

public record GitPullRequestDiffDetails(
        UUID repositoryId,
        int pullRequestNumber,
        String baseSha,
        String headSha,
        List<GitDiffFileDetails> files) {

    public GitPullRequestDiffDetails {
        GitValues.requireId(repositoryId, "repository_id is required");
        if (pullRequestNumber < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        baseSha = GitValues.requireTrimmed(baseSha, "base_sha is required");
        headSha = GitValues.requireTrimmed(headSha, "head_sha is required");
        files = List.copyOf(files == null ? List.of() : files);
    }
}
