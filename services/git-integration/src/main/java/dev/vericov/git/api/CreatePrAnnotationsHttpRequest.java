package dev.vericov.git.api;

import jakarta.json.bind.annotation.JsonbProperty;
import java.util.List;

public record CreatePrAnnotationsHttpRequest(
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
        @JsonbProperty("annotation_batch_key")
        String annotationBatchKey,
        List<GitAnnotationHttpRequest> annotations) {
}
