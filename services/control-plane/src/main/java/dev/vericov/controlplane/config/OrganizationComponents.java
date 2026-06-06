package dev.vericov.controlplane.config;

import dev.vericov.controlplane.adapter.auth.ServiceJwtVerifier;
import dev.vericov.controlplane.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.controlplane.adapter.jdbc.JdbcOrganizationRepository;
import dev.vericov.controlplane.application.InMemoryOrganizationRepository;
import dev.vericov.controlplane.application.OrganizationApplicationService;
import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.port.InternalServiceAuthorizer;
import dev.vericov.controlplane.application.port.OrganizationRepository;
import dev.vericov.controlplane.application.port.UserPrincipalResolver;
import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

@ApplicationScoped
public class OrganizationComponents {

    @Produces
    @ApplicationScoped
    public OrganizationApplicationService organizationApplicationService(OrganizationRepository repository) {
        return new OrganizationApplicationService(repository, Clock.systemUTC());
    }

    @Produces
    @ApplicationScoped
    public OrganizationRepository organizationRepository() {
        String jdbcUrl = env("VERICOV_CONTROL_PLANE_DB_URL", env("VERICOV_DB_URL", env("SUPABASE_DB_URL", "")));
        if (jdbcUrl.isBlank()) {
            return new InMemoryOrganizationRepository();
        }
        return new JdbcOrganizationRepository(dataSource(jdbcUrl));
    }

    @Produces
    @ApplicationScoped
    public UserPrincipalResolver userPrincipalResolver() {
        if (Boolean.parseBoolean(env("VERICOV_DEV_AUTH_BYPASS", "false"))) {
            return new DevelopmentUserPrincipalResolver();
        }
        String publicKey = env("VERICOV_SERVICE_JWT_PUBLIC_KEY", "");
        String secret = env("VERICOV_SERVICE_JWT_SECRET", "");
        if (publicKey.isBlank() && secret.isBlank()) {
            throw new IllegalStateException(
                    "VERICOV_SERVICE_JWT_PUBLIC_KEY or VERICOV_SERVICE_JWT_SECRET is required");
        }
        return new ServiceJwtVerifier(
                publicKey,
                secret,
                env("VERICOV_SERVICE_JWT_ISSUER", "veriapi"),
                env("VERICOV_SERVICE_JWT_AUDIENCE", "vericov"),
                Clock.systemUTC());
    }

    @Produces
    @ApplicationScoped
    public InternalServiceAuthorizer internalServiceAuthorizer() {
        return new EnvironmentInternalServiceAuthorizer(internalServiceTokenHashesByService());
    }

    private static DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(
                jdbcUrl,
                requiredDbEnv("VERICOV_CONTROL_PLANE_DB_USER", "VERICOV_DB_USER", "SUPABASE_DB_USER"),
                requiredDbEnv("VERICOV_CONTROL_PLANE_DB_PASSWORD", "VERICOV_DB_PASSWORD", "SUPABASE_DB_PASSWORD"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when control-plane JDBC is configured");
        }
        return value;
    }

    private static String requiredDbEnv(String primaryName, String secondaryName, String fallbackName) {
        for (String name : List.of(primaryName, secondaryName, fallbackName)) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return requiredEnv(primaryName);
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

    private record EnvironmentInternalServiceAuthorizer(
            Map<String, String> tokenHashesByService) implements InternalServiceAuthorizer {

        private EnvironmentInternalServiceAuthorizer {
            tokenHashesByService = Map.copyOf(tokenHashesByService == null ? Map.of() : tokenHashesByService);
        }

        @Override
        public String requireAuthorizedService(String serviceName, String serviceToken) {
            if (serviceName == null || serviceName.isBlank()) {
                throw new OrganizationException("unauthorized", "Service identity is required");
            }
            if (serviceToken == null || serviceToken.isBlank()) {
                throw new OrganizationException("unauthorized", "Service proof token is required");
            }
            if (tokenHashesByService.isEmpty()) {
                throw new OrganizationException("unauthorized", "Internal service authorization is not configured");
            }
            String normalizedServiceName = serviceName.trim();
            String expectedHash = tokenHashesByService.get(normalizedServiceName);
            if (expectedHash == null
                    || !MessageDigest.isEqual(sha256(serviceToken.trim()), HexFormat.of().parseHex(expectedHash))) {
                throw new OrganizationException("unauthorized", "Internal service authorization failed");
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

    private static final class DevelopmentUserPrincipalResolver implements UserPrincipalResolver {
        @Override
        public AuthenticatedUser resolve(UserAuthContext context) {
            String userId = firstPresent(context.userIdHeader(), System.getenv("VERICOV_DEV_USER_ID"));
            if (userId == null) {
                throw new OrganizationException(
                        "unauthorized",
                        "X-Vericov-User-Id or VERICOV_DEV_USER_ID is required until Supabase JWT validation is configured");
            }
            try {
                return new AuthenticatedUser(UUID.fromString(userId), System.getenv("VERICOV_DEV_USER_EMAIL"));
            } catch (IllegalArgumentException exception) {
                throw new OrganizationException("unauthorized", "Authenticated user id is invalid");
            }
        }

        private static String firstPresent(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first.trim();
            }
            if (second != null && !second.isBlank()) {
                return second.trim();
            }
            return null;
        }
    }
}
