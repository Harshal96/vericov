package dev.vericov.git.api;

import jakarta.json.bind.annotation.JsonbProperty;

public record CreateBranchHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("repository_id")
        String repositoryId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("branch_name")
        String branchName,
        @JsonbProperty("base_sha")
        String baseSha,
        @JsonbProperty("idempotency_key")
        String idempotencyKey) {
}
