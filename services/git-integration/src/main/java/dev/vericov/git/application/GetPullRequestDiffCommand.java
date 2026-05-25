package dev.vericov.git.application;

import java.util.UUID;

public record GetPullRequestDiffCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        int pullRequestNumber,
        String baseSha,
        String headSha) {

    public GetPullRequestDiffCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        if (pullRequestNumber < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        baseSha = GitValues.trimOptional(baseSha);
        headSha = GitValues.requireTrimmed(headSha, "head_sha is required");
    }
}
