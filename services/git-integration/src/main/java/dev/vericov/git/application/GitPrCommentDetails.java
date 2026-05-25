package dev.vericov.git.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GitPrCommentDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        int pullRequestNumber,
        String commentKey,
        String providerCommentId,
        String bodyHash,
        String status,
        String providerUrl,
        Instant createdAt,
        Instant updatedAt) {

    public GitPrCommentDetails {
        GitValues.requireId(id, "id is required");
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        if (pullRequestNumber < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        commentKey = GitValues.requireTrimmed(commentKey, "comment_key is required");
        providerCommentId = GitValues.trimOptional(providerCommentId);
        bodyHash = GitValues.requireTrimmed(bodyHash, "body_hash is required");
        status = GitValues.requireCanonical(status, "status is required");
        providerUrl = GitValues.trimOptional(providerUrl);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
