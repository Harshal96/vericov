package dev.vericov.git.application.port;

import java.util.UUID;

public interface IntegrationConfigClient {
    ResolvedGitIntegration resolveRepositoryIntegration(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String capability);

    CredentialLease leaseCredential(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind,
            String serviceName);
}
