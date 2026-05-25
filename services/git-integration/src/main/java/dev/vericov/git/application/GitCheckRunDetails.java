package dev.vericov.git.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GitCheckRunDetails(
        UUID id,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String commitSha,
        String name,
        String providerCheckId,
        String status,
        String conclusion,
        String detailsUrl,
        Map<String, Object> output,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public GitCheckRunDetails {
        GitValues.requireId(id, "id is required");
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        commitSha = GitValues.requireTrimmed(commitSha, "commit_sha is required");
        name = GitValues.requireTrimmed(name, "name is required");
        providerCheckId = GitValues.trimOptional(providerCheckId);
        status = GitValues.requireCanonical(status, "status is required");
        conclusion = GitValues.trimOptional(conclusion);
        detailsUrl = GitValues.trimOptional(detailsUrl);
        output = GitValues.deepCopyMap(output);
        idempotencyKey = GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
