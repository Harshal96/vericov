package dev.vericov.git.application;

import java.util.UUID;

public record CreateOrUpdatePrCommentCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        int pullRequestNumber,
        String marker,
        String body) {

    public CreateOrUpdatePrCommentCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        if (pullRequestNumber < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        marker = GitValues.requireTrimmed(marker, "marker is required");
        body = GitValues.requireTrimmed(body, "body is required");
    }
}
