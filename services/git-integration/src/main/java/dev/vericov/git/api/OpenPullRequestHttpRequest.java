package dev.vericov.git.api;

import jakarta.json.bind.annotation.JsonbProperty;

public record OpenPullRequestHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("repository_id")
        String repositoryId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("source_branch")
        String sourceBranch,
        @JsonbProperty("target_branch")
        String targetBranch,
        String title,
        String body,
        boolean draft,
        @JsonbProperty("idempotency_key")
        String idempotencyKey) {
}
