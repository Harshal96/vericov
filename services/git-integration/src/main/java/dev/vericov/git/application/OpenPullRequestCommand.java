package dev.vericov.git.application;

import java.util.UUID;

public record OpenPullRequestCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String sourceBranch,
        String targetBranch,
        String title,
        String body,
        boolean draft,
        String idempotencyKey) {

    public OpenPullRequestCommand(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String sourceBranch,
            String targetBranch,
            String title,
            String body) {
        this(tenantId, orgId, repositoryId, providerKey, sourceBranch, targetBranch, title, body, false,
                title + "-" + sourceBranch + "-" + targetBranch);
    }

    public OpenPullRequestCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        sourceBranch = GitValues.requireTrimmed(sourceBranch, "source_branch is required");
        targetBranch = GitValues.requireTrimmed(targetBranch, "target_branch is required");
        title = GitValues.requireTrimmed(title, "title is required");
        body = GitValues.requireTrimmed(body, "body is required");
        idempotencyKey = GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required");
    }
}
