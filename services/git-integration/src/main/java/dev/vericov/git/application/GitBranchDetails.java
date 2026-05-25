package dev.vericov.git.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GitBranchDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String branchName,
        String baseSha,
        String providerRef,
        String status,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public GitBranchDetails {
        GitValues.requireId(id, "id is required");
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        branchName = GitValues.requireTrimmed(branchName, "branch_name is required");
        baseSha = GitValues.requireTrimmed(baseSha, "base_sha is required");
        providerRef = GitValues.trimOptional(providerRef);
        status = GitValues.requireCanonical(status, "status is required");
        idempotencyKey = GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
