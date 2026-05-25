package dev.vericov.git.application;

import java.util.List;
import java.util.UUID;

public record CreateOrUpdatePrAnnotationsCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        int pullRequestNumber,
        String annotationBatchKey,
        List<GitAnnotationInput> annotations) {

    public CreateOrUpdatePrAnnotationsCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        if (pullRequestNumber < 1) {
            throw new GitIntegrationException("validation_error", "pull_request_number must be positive");
        }
        annotationBatchKey = GitValues.requireTrimmed(annotationBatchKey, "annotation_batch_key is required");
        annotations = List.copyOf(annotations == null ? List.of() : annotations);
        if (annotations.isEmpty()) {
            throw new GitIntegrationException("validation_error", "annotations are required");
        }
    }
}
