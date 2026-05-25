package dev.vericov.git.application;

import java.util.UUID;

public record CreateBranchCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String branchName,
        String baseSha,
        String idempotencyKey) {

    public CreateBranchCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        branchName = GitValues.requireTrimmed(branchName, "branch_name is required");
        baseSha = GitValues.requireTrimmed(baseSha, "base_sha is required");
        idempotencyKey = GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required");
    }
}
