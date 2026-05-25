package dev.vericov.git.adapter.provider.github;

import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionType;
import dev.vericov.git.application.port.CredentialLease;
import java.net.URI;
import java.net.http.HttpRequest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubInstallationTokenProviderTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void exchangesGithubAppPrivateKeyForInstallationToken() throws Exception {
        RecordingTransport transport = new RecordingTransport("""
                {"token":"ghs_installation_token","expires_at":"2026-05-23T20:00:00Z"}
                """);
        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(
                URI.create("https://api.github.test"),
                Clock.fixed(Instant.parse("2026-05-23T19:00:00Z"), ZoneOffset.UTC),
                transport);

        String token = provider.accessToken(action("github_app_private_key", privateKeyPem().toCharArray()));

        assertEquals("ghs_installation_token", token);
        assertEquals("POST", transport.lastRequest.method());
        assertEquals("/app/installations/123456/access_tokens", transport.lastRequest.uri().getPath());
        assertTrue(transport.lastRequest.headers().firstValue("Authorization").orElseThrow().startsWith("Bearer "));
    }

    @Test
    void returnsLeasedInstallationTokenWithoutExchange() {
        GitHubInstallationTokenProvider provider = new GitHubInstallationTokenProvider(
                URI.create("https://api.github.test"),
                Clock.systemUTC(),
                (request, body) -> {
                    throw new AssertionError("installation token leases must not call GitHub");
                });

        assertEquals("already-a-token", provider.accessToken(action(
                "github_installation_token",
                "already-a-token".toCharArray())));
    }

    private static GitProviderAction action(String credentialKind, char[] secret) {
        return new GitProviderAction(
                GitProviderActionType.CREATE_OR_UPDATE_CHECK_RUN,
                TENANT_ID,
                ORG_ID,
                REPOSITORY_ID,
                CONNECTION_ID,
                "github",
                "acme/widget",
                "git.checks",
                credentialKind,
                new CredentialLease(UUID.randomUUID(), credentialKind, secret, Instant.parse("2026-05-23T20:00:00Z")),
                Map.of("installation_id", "123456", "app_id", "98765"),
                Map.of(),
                Map.of(
                        "check_name", "Vericov Coverage",
                        "commit_sha", "abc123",
                        "status", "completed",
                        "idempotency_key", "check-key"));
    }

    private static String privateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }

    private static final class RecordingTransport implements GitHubInstallationTokenProvider.HttpTransport {
        private final String responseBody;
        private HttpRequest lastRequest;

        private RecordingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public GitHubInstallationTokenProvider.HttpResult send(HttpRequest request, String body) {
            lastRequest = request;
            return new GitHubInstallationTokenProvider.HttpResult(201, responseBody);
        }
    }
}
