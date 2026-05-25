package dev.vericov.git.application;

import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitActionRepository;
import dev.vericov.git.application.port.GitProviderPullRequestDiffQuery;
import dev.vericov.git.application.port.GitProviderQueryPort;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import java.util.Objects;
import java.util.UUID;

public class GitProviderQueryService {
    private static final String SERVICE_NAME = "git-integration";
    private static final String ACTIVE_STATUS = "active";
    private static final String REPOSITORY_SYNC_CAPABILITY = "git.repository_sync";

    private final IntegrationConfigClient integrationConfigClient;
    private final GitProviderQueryPort providerQueryPort;
    private final GitActionRepository actionRepository;

    public GitProviderQueryService(
            IntegrationConfigClient integrationConfigClient,
            GitProviderQueryPort providerQueryPort,
            GitActionRepository actionRepository) {
        this.integrationConfigClient = Objects.requireNonNull(integrationConfigClient, "integrationConfigClient");
        this.providerQueryPort = Objects.requireNonNull(providerQueryPort, "providerQueryPort");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository");
    }

    public GitPullRequestDiffDetails getPullRequestDiff(GetPullRequestDiffCommand command) {
        Objects.requireNonNull(command, "command");
        GitPullRequestDetails pullRequest = actionRepository.findPullRequest(
                        command.tenantId(),
                        command.repositoryId(),
                        command.providerKey(),
                        command.pullRequestNumber())
                .orElseThrow(() -> new GitIntegrationException("not_found", "Pull request not found"));
        String baseSha = command.baseSha() == null ? pullRequest.baseSha() : command.baseSha();
        if (!baseSha.equals(pullRequest.baseSha()) || !command.headSha().equals(pullRequest.headSha())) {
            throw new GitIntegrationException("conflict", "Requested base/head does not match recorded pull request");
        }

        ResolvedGitIntegration resolved = integrationConfigClient.resolveRepositoryIntegration(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                REPOSITORY_SYNC_CAPABILITY);
        verifyResolvedBinding(
                resolved,
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                command.providerKey(),
                REPOSITORY_SYNC_CAPABILITY);
        String credentialKind = GitValues.requireCanonical(resolved.credentialKind(), "credential_kind is required");
        CredentialLease credentialLease = integrationConfigClient.leaseCredential(
                command.tenantId(),
                command.orgId(),
                resolved.connectionId(),
                credentialKind,
                SERVICE_NAME);
        if (credentialLease == null) {
            throw new GitIntegrationException("not_found", "Integration credential lease not found");
        }
        if (!credentialKind.equals(credentialLease.credentialKind())) {
            throw new GitIntegrationException("validation_error", "Integration credential lease kind does not match resolution");
        }
        return providerQueryPort.fetchPullRequestDiff(new GitProviderPullRequestDiffQuery(
                command.tenantId(),
                command.orgId(),
                command.repositoryId(),
                resolved.connectionId(),
                command.providerKey(),
                resolved.externalRepositoryId(),
                REPOSITORY_SYNC_CAPABILITY,
                credentialKind,
                credentialLease,
                resolved.connectionConfig(),
                resolved.bindingConfig(),
                command.pullRequestNumber(),
                baseSha,
                command.headSha()));
    }

    private static void verifyResolvedBinding(
            ResolvedGitIntegration resolved,
            UUID tenantId,
            UUID orgId,
            UUID repositoryId,
            String providerKey,
            String requiredCapability) {
        if (resolved == null) {
            throw new GitIntegrationException("not_found", "Integration resolution not found");
        }
        if (!tenantId.equals(resolved.tenantId())
                || !orgId.equals(resolved.orgId())
                || !repositoryId.equals(resolved.repositoryId())
                || !providerKey.equals(resolved.providerKey())) {
            throw new GitIntegrationException("validation_error", "Integration resolution does not match request");
        }
        if (!ACTIVE_STATUS.equals(resolved.connectionStatus())) {
            throw new GitIntegrationException("not_found", "Active integration connection not found");
        }
        if (!ACTIVE_STATUS.equals(resolved.bindingStatus())) {
            throw new GitIntegrationException("not_found", "Active integration binding not found");
        }
        if (!resolved.grantsCapability(requiredCapability)) {
            throw new GitIntegrationException("forbidden", "Integration binding does not grant " + requiredCapability);
        }
    }
}
