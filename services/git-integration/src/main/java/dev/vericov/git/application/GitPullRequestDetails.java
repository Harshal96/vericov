package dev.vericov.git.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GitPullRequestDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String providerPullRequestId,
        int number,
        String title,
        String author,
        String baseBranch,
        String baseSha,
        String headBranch,
        String headSha,
        String state,
        String providerUrl,
        Instant createdAt,
        Instant updatedAt) {

    public GitPullRequestDetails {
        GitValues.requireId(id, "id is required");
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        providerPullRequestId = GitValues.requireTrimmed(providerPullRequestId, "provider_pull_request_id is required");
        if (number < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        title = GitValues.requireTrimmed(title, "title is required");
        author = GitValues.requireTrimmed(author, "author is required");
        baseBranch = GitValues.requireTrimmed(baseBranch, "base_branch is required");
        baseSha = GitValues.requireTrimmed(baseSha, "base_sha is required");
        headBranch = GitValues.requireTrimmed(headBranch, "head_branch is required");
        headSha = GitValues.requireTrimmed(headSha, "head_sha is required");
        state = GitValues.requireCanonical(state, "state is required");
        providerUrl = GitValues.trimOptional(providerUrl);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
