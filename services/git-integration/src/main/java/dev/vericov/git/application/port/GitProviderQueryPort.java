package dev.vericov.git.application.port;

import dev.vericov.git.application.GitPullRequestDiffDetails;

public interface GitProviderQueryPort {
    GitPullRequestDiffDetails fetchPullRequestDiff(GitProviderPullRequestDiffQuery query);
}
