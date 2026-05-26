package dev.vericov.agent.config;

import dev.vericov.agent.adapter.jdbc.AgentJsonCodec;
import dev.vericov.agent.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.agent.adapter.jdbc.JdbcAgentTaskRepository;
import dev.vericov.agent.application.AgentControlPlaneService;
import dev.vericov.agent.application.AgentRunnerException;
import dev.vericov.agent.application.InMemoryAgentTaskRepository;
import dev.vericov.agent.application.port.AgentEventPublisher;
import dev.vericov.agent.application.port.AgentTaskRepository;
import dev.vericov.agent.application.port.InternalServiceAuthorizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class AgentRunnerComponents {
    @Produces
    @ApplicationScoped
    public AgentControlPlaneService agentControlPlaneService(
            AgentTaskRepository repository,
            AgentEventPublisher eventPublisher,
            Clock clock) {
        return new AgentControlPlaneService(repository, eventPublisher, clock);
    }

    @Produces
    @ApplicationScoped
    public AgentTaskRepository agentTaskRepository() {
        DriverManagerDataSource dataSource = dataSource();
        if (dataSource != null) {
            return new JdbcAgentTaskRepository(dataSource, new AgentJsonCodec());
        }
        return new InMemoryAgentTaskRepository();
    }

    @Produces
    @ApplicationScoped
    public AgentEventPublisher agentEventPublisher() {
        return event -> {
        };
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
                throw new AgentRunnerException("unauthorized", "Service identity is required");
            }
            if (serviceToken == null || serviceToken.isBlank()) {
                throw new AgentRunnerException("unauthorized", "Service proof token is required");
            }
            if (tokenHashesByService.isEmpty()) {
                throw new AgentRunnerException("unauthorized", "Internal service authorization is not configured");
            }
            String expectedHash = tokenHashesByService.get(serviceName.trim());
            if (expectedHash == null) {
                throw new AgentRunnerException("unauthorized", "Service identity is not authorized");
            }
            String actualHash = sha256(serviceToken);
            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    actualHash.getBytes(StandardCharsets.UTF_8))) {
                throw new AgentRunnerException("unauthorized", "Service proof token is invalid");
            }
            return serviceName.trim();
        }

        private static String sha256(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }
    }
}
