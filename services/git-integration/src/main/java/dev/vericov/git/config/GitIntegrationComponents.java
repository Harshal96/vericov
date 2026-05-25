package dev.vericov.git.config;

import dev.vericov.git.adapter.integrations.InternalIntegrationConfigHttpClient;
import dev.vericov.git.adapter.integrations.InternalIntegrationEventPublisher;
import dev.vericov.git.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.git.adapter.jdbc.GitJsonCodec;
import dev.vericov.git.adapter.jdbc.JdbcGitActionRepository;
import dev.vericov.git.adapter.provider.DefaultGitProviderClientFactory;
import dev.vericov.git.adapter.provider.GitProviderClientActionPort;
import dev.vericov.git.adapter.provider.github.GitHubInstallationTokenProvider;
import dev.vericov.git.adapter.provider.github.GitHubProviderClient;
import dev.vericov.git.adapter.provider.github.GitHubWebhookVerifier;
import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitProviderActionService;
import dev.vericov.git.application.GitProviderQueryService;
import dev.vericov.git.application.GitWebhookService;
import dev.vericov.git.application.InMemoryGitActionRepository;
import dev.vericov.git.application.PublishedGitEvent;
import dev.vericov.git.application.port.GitActionRepository;
import dev.vericov.git.application.port.GitEventPublisher;
import dev.vericov.git.application.port.GitProviderActionPort;
import dev.vericov.git.application.port.GitProviderClient;
import dev.vericov.git.application.port.GitProviderClientFactory;
import dev.vericov.git.application.port.GitProviderQueryPort;
import dev.vericov.git.application.port.GitWebhookVerifier;
import dev.vericov.git.application.port.IntegrationConfigClient;
import dev.vericov.git.application.port.InternalServiceAuthorizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class GitIntegrationComponents {
    private static final String SERVICE_NAME = "git-integration";

    @Produces
    @ApplicationScoped
    public GitProviderActionService gitProviderActionService(
            IntegrationConfigClient integrationConfigClient,
            GitProviderActionPort providerActionPort,
            GitActionRepository actionRepository) {
        return new GitProviderActionService(integrationConfigClient, providerActionPort, actionRepository);
    }

    @Produces
    @ApplicationScoped
    public GitWebhookService gitWebhookService(
            GitActionRepository actionRepository,
            GitWebhookVerifier webhookVerifier,
            GitEventPublisher eventPublisher) {
        return new GitWebhookService(actionRepository, webhookVerifier, eventPublisher);
    }

    @Produces
    @ApplicationScoped
    public GitProviderQueryService gitProviderQueryService(
            IntegrationConfigClient integrationConfigClient,
            GitProviderQueryPort providerQueryPort,
            GitActionRepository actionRepository) {
        return new GitProviderQueryService(integrationConfigClient, providerQueryPort, actionRepository);
    }

    @Produces
    @ApplicationScoped
    public GitActionRepository gitActionRepository() {
        DriverManagerDataSource dataSource = dataSource();
        if (dataSource != null) {
            return new JdbcGitActionRepository(dataSource, new GitJsonCodec());
        }
        return new InMemoryGitActionRepository();
    }

    @Produces
    @ApplicationScoped
    public IntegrationConfigClient integrationConfigClient() {
        String token = env("VERICOV_INTERNAL_SERVICE_TOKEN", "");
        if (token.isBlank()) {
            return new UnavailableIntegrationConfigClient();
        }
        return new InternalIntegrationConfigHttpClient(
                URI.create(env("VERICOV_INTEGRATIONS_BASE_URL", "http://127.0.0.1:8084")),
                SERVICE_NAME,
                token);
    }

    @Produces
    @ApplicationScoped
    public GitProviderActionPort gitProviderActionPort(GitProviderClientFactory clientFactory) {
        return new GitProviderClientActionPort(clientFactory);
    }

    @Produces
    @ApplicationScoped
    public GitProviderClientFactory gitProviderClientFactory(GitHubProviderClient githubProviderClient) {
        return new DefaultGitProviderClientFactory(Map.of("github", githubProviderClient));
    }

    @Produces
    @ApplicationScoped
    public GitHubProviderClient githubProviderClient(Clock clock) {
        GitHubInstallationTokenProvider tokenProvider = new GitHubInstallationTokenProvider(
                URI.create(env("VERICOV_GITHUB_API_BASE_URL", "https://api.github.com")),
                clock);
        return new GitHubProviderClient(
                URI.create(env("VERICOV_GITHUB_API_BASE_URL", "https://api.github.com")),
                tokenProvider);
    }

    @Produces
    @ApplicationScoped
    public GitProviderQueryPort gitProviderQueryPort(GitHubProviderClient githubProviderClient) {
        return query -> {
            if (!"github".equals(query.providerKey())) {
                throw new GitIntegrationException("unsupported_provider", "Unsupported git provider");
            }
            return githubProviderClient.fetchPullRequestDiff(query);
        };
    }

    @Produces
    @ApplicationScoped
    public GitWebhookVerifier gitWebhookVerifier() {
        String secret = env("VERICOV_GITHUB_WEBHOOK_SECRET", "");
        if (secret.isBlank()) {
            return new RejectingWebhookVerifier();
        }
        return new GitHubWebhookVerifier(secret.toCharArray());
    }

    @Produces
    @ApplicationScoped
    public GitEventPublisher gitEventPublisher() {
        String token = env("VERICOV_INTERNAL_SERVICE_TOKEN", "");
        if (token.isBlank()) {
            return new NoopGitEventPublisher();
        }
        return new InternalIntegrationEventPublisher(
                URI.create(env("VERICOV_INTEGRATIONS_BASE_URL", "http://127.0.0.1:8084")),
                SERVICE_NAME,
                token);
    }

    @Produces
    @ApplicationScoped
    public InternalServiceAuthorizer internalServiceAuthorizer() {
        return new EnvironmentInternalServiceAuthorizer(internalServiceTokenHashesByService());
    }

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }

    private static DriverManagerDataSource dataSource() {
        String jdbcUrl = env("VERICOV_DATABASE_URL", env("SUPABASE_DB_URL", ""));
        if (jdbcUrl.isBlank()) {
            return null;
        }
        return new DriverManagerDataSource(
                jdbcUrl,
                env("VERICOV_DATABASE_USER", env("SUPABASE_DB_USER", "")),
                env("VERICOV_DATABASE_PASSWORD", env("SUPABASE_DB_PASSWORD", "")));
    }

    private static Map<String, String> internalServiceTokenHashesByService() {
        String configured = env("VERICOV_INTERNAL_SERVICE_TOKEN_SHA256", "");
        if (configured.isBlank()) {
            return Map.of();
        }
        Map<String, String> hashesByService = new LinkedHashMap<>();
        for (String entry : configured.split("[,\\s]+")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException(
                        "VERICOV_INTERNAL_SERVICE_TOKEN_SHA256 entries must use service_name=sha256_hex");
            }
            String hash = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException(
                        "VERICOV_INTERNAL_SERVICE_TOKEN_SHA256 contains an invalid SHA-256 hash");
            }
            hashesByService.put(parts[0].trim(), hash);
        }
        return Map.copyOf(hashesByService);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record EnvironmentInternalServiceAuthorizer(
            Map<String, String> tokenHashesByService) implements InternalServiceAuthorizer {

        private EnvironmentInternalServiceAuthorizer {
            tokenHashesByService = Map.copyOf(tokenHashesByService == null ? Map.of() : tokenHashesByService);
        }

        @Override
        public String requireAuthorizedService(String serviceName, String serviceToken) {
            if (serviceName == null || serviceName.isBlank()) {
                throw new GitIntegrationException("unauthorized", "Service identity is required");
            }
            if (serviceToken == null || serviceToken.isBlank()) {
                throw new GitIntegrationException("unauthorized", "Service proof token is required");
            }
            if (tokenHashesByService.isEmpty()) {
                throw new GitIntegrationException("unauthorized", "Internal service authorization is not configured");
            }
            String normalizedServiceName = serviceName.trim();
            String expectedHash = tokenHashesByService.get(normalizedServiceName);
            if (expectedHash == null
                    || !MessageDigest.isEqual(sha256(serviceToken.trim()), HexFormat.of().parseHex(expectedHash))) {
                throw new GitIntegrationException("unauthorized", "Internal service authorization failed");
            }
            return normalizedServiceName;
        }

        private static byte[] sha256(String value) {
            try {
                return MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    private static final class RejectingWebhookVerifier implements GitWebhookVerifier {
        @Override
        public boolean verify(String providerKey, String eventType, String deliveryId, String signature, byte[] payload) {
            return false;
        }
    }

    private static final class NoopGitEventPublisher implements GitEventPublisher {
        @Override
        public void publish(PublishedGitEvent event) {
        }
    }

    private static final class UnavailableIntegrationConfigClient implements IntegrationConfigClient {
        @Override
        public dev.vericov.git.application.port.ResolvedGitIntegration resolveRepositoryIntegration(
                java.util.UUID tenantId,
                java.util.UUID orgId,
                java.util.UUID repositoryId,
                String providerKey,
                String capability) {
            throw new GitIntegrationException("unauthorized", "Integration Config client is not configured");
        }

        @Override
        public dev.vericov.git.application.port.CredentialLease leaseCredential(
                java.util.UUID tenantId,
                java.util.UUID orgId,
                java.util.UUID connectionId,
                String credentialKind,
                String serviceName) {
            throw new GitIntegrationException("unauthorized", "Integration Config client is not configured");
        }
    }
}
