package dev.vericov.git.application;

import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitProviderPullRequestDiffQuery;
import dev.vericov.git.application.port.GitProviderQueryPort;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.ResolvedGitIntegration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitProviderQueryServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void rejectsDiffWhenStoredPullRequestHeadDoesNotMatchRequestedHead() {
        Fixture fixture = new Fixture("base123", "storedHead");

        GitIntegrationException exception = assertThrows(GitIntegrationException.class, () -> fixture.service().getPullRequestDiff(
                new GetPullRequestDiffCommand(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", 42, "base123", "requestedHead")));

        assertEquals("conflict", exception.code());
    }

    @Test
    void fetchesDiffForRecordedBaseAndHead() {
        Fixture fixture = new Fixture("base123", "head456");

        GitPullRequestDiffDetails diff = fixture.service().getPullRequestDiff(
                new GetPullRequestDiffCommand(TENANT_ID, ORG_ID, REPOSITORY_ID, "github", 42, "base123", "head456"));

        assertEquals("base123", diff.baseSha());
        assertEquals("head456", diff.headSha());
        assertEquals("src/App.java", diff.files().getFirst().filePath());
        assertEquals("git.repository_sync", fixture.integrationConfigClient.resolveCapability);
        assertEquals("acme/widget", fixture.queryPort.lastQuery.externalRepositoryId());
    }

    private static final class Fixture {
        private final InMemoryGitActionRepository repository = new InMemoryGitActionRepository();
        private final RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
        private final RecordingGitProviderQueryPort queryPort = new RecordingGitProviderQueryPort();

        private Fixture(String baseSha, String headSha) {
            repository.savePullRequest(new GitPullRequestDetails(
                    UUID.randomUUID(),
                    TENANT_ID,
                    ORG_ID,
                    REPOSITORY_ID,
                    "github",
                    "provider-pr-42",
                    42,
                    "Add coverage",
                    "octocat",
                    "main",
                    baseSha,
                    "feature",
                    headSha,
                    "open",
                    "https://github.test/acme/widget/pull/42",
                    NOW,
                    NOW));
        }

        private GitProviderQueryService service() {
            return new GitProviderQueryService(integrationConfigClient, queryPort, repository);
        }
    }

    private static final class RecordingIntegrationConfigClient implements IntegrationConfigClient {
        private String resolveCapability;

        @Override
        public ResolvedGitIntegration resolveRepositoryIntegration(
                UUID tenantId,
                UUID orgId,
                UUID repositoryId,
                String providerKey,
                String capability) {
            resolveCapability = capability;
            return new ResolvedGitIntegration(
                    TENANT_ID,
                    ORG_ID,
                    CONNECTION_ID,
                    REPOSITORY_ID,
                    "github",
                    "active",
                    "active",
                    "acme/widget",
                    "github_installation_token",
                    Set.of("git.repository_sync"),
                    Map.of("installation_id", "123456"),
                    Map.of());
        }

        @Override
        public CredentialLease leaseCredential(
                UUID tenantId,
                UUID orgId,
                UUID connectionId,
                String credentialKind,
                String serviceName) {
            return new CredentialLease(UUID.randomUUID(), credentialKind, "token".toCharArray(), NOW.plusSeconds(300));
        }
    }

    private static final class RecordingGitProviderQueryPort implements GitProviderQueryPort {
        private GitProviderPullRequestDiffQuery lastQuery;

        @Override
        public GitPullRequestDiffDetails fetchPullRequestDiff(GitProviderPullRequestDiffQuery query) {
            lastQuery = query;
            return new GitPullRequestDiffDetails(
                    query.repositoryId(),
                    query.pullRequestNumber(),
                    query.baseSha(),
                    query.headSha(),
                    List.of(new GitDiffFileDetails(
                            "src/App.java",
                            null,
                            "modified",
                            List.of(new GitDiffLineDetails(null, 10, "added")))));
        }
    }
}
