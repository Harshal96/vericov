package dev.vericov.git.api;

import jakarta.json.bind.annotation.JsonbProperty;

public record CreatePrCommentHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("repository_id")
        String repositoryId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("pull_request_number")
        int pullRequestNumber,
        String marker,
        String body) {
}
