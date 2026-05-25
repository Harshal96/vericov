package dev.vericov.git.application.port;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GitProviderPullRequestDiffQuery(
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
        int pullRequestNumber,
        String baseSha,
        String headSha) {

    public GitProviderPullRequestDiffQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(connectionId, "connectionId");
        providerKey = required(providerKey, "providerKey");
        externalRepositoryId = required(externalRepositoryId, "externalRepositoryId");
        requiredCapability = required(requiredCapability, "requiredCapability");
        credentialKind = required(credentialKind, "credentialKind");
        Objects.requireNonNull(credentialLease, "credentialLease");
        connectionConfig = Map.copyOf(connectionConfig == null ? Map.of() : connectionConfig);
        bindingConfig = Map.copyOf(bindingConfig == null ? Map.of() : bindingConfig);
        if (pullRequestNumber < 1) {
            throw new IllegalArgumentException("pullRequestNumber must be positive");
        }
        baseSha = required(baseSha, "baseSha");
        headSha = required(headSha, "headSha");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
