package dev.vericov.git.adapter.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;
import dev.vericov.git.application.GitProviderActionType;
import dev.vericov.git.application.port.CredentialLease;
import dev.vericov.git.application.port.GitProviderClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultGitProviderClientFactoryTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void resolvesRegisteredProviderKeysCaseInsensitively() {
        GitProviderClient github = action -> new GitProviderActionResult(
                action.type(),
                "provider-id",
                "completed",
                "https://github.example/checks/1",
                Map.of());
        DefaultGitProviderClientFactory factory = new DefaultGitProviderClientFactory(Map.of("github", github));

        assertSame(github, factory.clientFor(" GitHub "));
    }

    @Test
    void actionPortDelegatesToResolvedProviderClient() {
        GitProviderClientActionPort actionPort = new GitProviderClientActionPort(providerKey -> action ->
                new GitProviderActionResult(action.type(), providerKey + "-id", "completed", null, Map.of()));

        GitProviderActionResult result = actionPort.execute(action("github"));

        assertEquals("github-id", result.providerId());
    }

    @Test
    void unsupportedProviderClientFailsWithActionableCode() {
        DefaultGitProviderClientFactory factory = new DefaultGitProviderClientFactory(Map.of());

        GitIntegrationException exception = assertThrows(
                GitIntegrationException.class,
                () -> factory.clientFor("bitbucket").execute(action("bitbucket")));

        assertEquals("unsupported_provider", exception.code());
    }

    private static GitProviderAction action(String providerKey) {
        return new GitProviderAction(
                GitProviderActionType.CREATE_BRANCH,
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                providerKey,
                "vericov/app",
                "git.branches",
                "github_app_private_key",
                new CredentialLease(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        "github_app_private_key",
                        "secret".toCharArray(),
                        Instant.parse("2026-05-22T10:20:30Z")),
                Map.of("branch", "coverage-fix"));
    }
}
