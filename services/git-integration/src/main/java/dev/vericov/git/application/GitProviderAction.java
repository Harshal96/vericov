package dev.vericov.git.application;

import dev.vericov.git.application.port.CredentialLease;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GitProviderAction(
        GitProviderActionType type,
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        UUID connectionId,
        String providerKey,
        String externalRepositoryId,
        String requiredCapability,
        String credentialKind,
        CredentialLease credentialLease,
        Map<String, Object> connectionConfig,
        Map<String, Object> bindingConfig,
        Map<String, Object> details) {

    public GitProviderAction {
        Objects.requireNonNull(type, "type");
        GitValues.requireId(tenantId, "tenant_id is required");
        GitValues.requireId(orgId, "org_id is required");
        GitValues.requireId(repositoryId, "repository_id is required");
        GitValues.requireId(connectionId, "connection_id is required");
        providerKey = GitValues.requireCanonical(providerKey, "provider_key is required");
        externalRepositoryId = GitValues.requireTrimmed(externalRepositoryId, "external_repository_id is required");
        requiredCapability = GitValues.requireCanonical(requiredCapability, "required_capability is required");
        credentialKind = GitValues.requireCanonical(credentialKind, "credential_kind is required");
        Objects.requireNonNull(credentialLease, "credentialLease");
        connectionConfig = GitValues.deepCopyMap(connectionConfig);
        bindingConfig = GitValues.deepCopyMap(bindingConfig);
        details = GitValues.deepCopyMap(details);
    }

    public GitProviderAction(
            GitProviderActionType type,
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            UUID connectionId,
            String providerKey,
            String externalRepositoryId,
            String requiredCapability,
            String credentialKind,
            CredentialLease credentialLease,
            Map<String, Object> details) {
        this(
                type,
                tenantId,
                orgId,
                repositoryId,
                connectionId,
                providerKey,
                externalRepositoryId,
                requiredCapability,
                credentialKind,
                credentialLease,
                Map.of(),
                Map.of(),
                details);
    }
}
