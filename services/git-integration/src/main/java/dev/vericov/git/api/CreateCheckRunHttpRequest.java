package dev.vericov.git.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CreateCheckRunHttpRequest(
        @JsonbProperty("tenant_id")
        String tenantId,
        @JsonbProperty("org_id")
        String orgId,
        @JsonbProperty("repository_id")
        String repositoryId,
        @JsonbProperty("provider_key")
        String providerKey,
        @JsonbProperty("commit_sha")
        String commitSha,
        String name,
        String status,
        String conclusion,
        String summary,
        String text,
        @JsonbProperty("details_url")
        String detailsUrl,
        List<GitAnnotationHttpRequest> annotations,
        @JsonbProperty("idempotency_key")
        String idempotencyKey) {
}
