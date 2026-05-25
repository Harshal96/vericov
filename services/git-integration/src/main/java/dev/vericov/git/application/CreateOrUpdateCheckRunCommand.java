package dev.vericov.git.application;

import java.util.UUID;
import java.util.List;

public record CreateOrUpdateCheckRunCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String checkName,
        String commitSha,
        String status,
        String conclusion,
        String summary,
        String text,
        String detailsUrl,
        List<GitAnnotationInput> annotations,
        String idempotencyKey) {

    public CreateOrUpdateCheckRunCommand(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String checkName,
            String commitSha,
            String status,
            String conclusion) {
        this(
                tenantId,
                orgId,
                repositoryId,
                providerKey,
                checkName,
                commitSha,
                status,
                conclusion,
                null,
                null,
                null,
                List.of(),
                checkName + "-" + commitSha);
    }

    public CreateOrUpdateCheckRunCommand {
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        checkName = GitValues.requireTrimmed(checkName, "check_name is required");
        commitSha = GitValues.requireTrimmed(commitSha, "commit_sha is required");
        status = GitValues.requireCanonical(status, "status is required");
        conclusion = GitValues.trimOptional(conclusion);
        summary = GitValues.trimOptional(summary);
        text = GitValues.trimOptional(text);
        detailsUrl = GitValues.trimOptional(detailsUrl);
        annotations = List.copyOf(annotations == null ? List.of() : annotations);
        idempotencyKey = GitValues.requireTrimmed(idempotencyKey, "idempotency_key is required");
    }
}
