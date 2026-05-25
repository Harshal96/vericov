package dev.vericov.integrations.config;

import dev.vericov.integrations.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.integrations.adapter.jdbc.IntegrationJsonCodec;
import dev.vericov.integrations.adapter.jdbc.JdbcIntegrationRepository;
import dev.vericov.integrations.adapter.jdbc.JdbcIntegrationScopeValidator;
import dev.vericov.integrations.application.CredentialLease;
import dev.vericov.integrations.application.InMemoryIntegrationRepository;
import dev.vericov.integrations.application.IntegrationApplicationService;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.CredentialVault;
import dev.vericov.integrations.application.port.InternalServiceAuthorizer;
import dev.vericov.integrations.application.port.IntegrationAuthorizer;
import dev.vericov.integrations.application.port.IntegrationRepository;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import dev.vericov.integrations.application.port.ProviderRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class DevelopmentIntegrationComponents {

    @Produces
    @ApplicationScoped
    public IntegrationApplicationService integrationApplicationService(
            IntegrationRepository repository,
            ProviderRegistry providerRegistry,
            CredentialVault credentialVault,
            IntegrationScopeValidator scopeValidator,
            Clock clock) {
        return new IntegrationApplicationService(repository, providerRegistry, credentialVault, scopeValidator, clock);
    }

    @Produces
    @ApplicationScoped
    public IntegrationRepository integrationRepository() {
        DriverManagerDataSource dataSource = integrationDataSource();
        if (dataSource != null) {
            return new JdbcIntegrationRepository(dataSource, new IntegrationJsonCodec());
        }
        return new InMemoryIntegrationRepository();
    }

    @Produces
    @ApplicationScoped
    public IntegrationScopeValidator integrationScopeValidator() {
        DriverManagerDataSource dataSource = integrationDataSource();
        if (dataSource != null) {
            return new JdbcIntegrationScopeValidator(dataSource);
        }
        return new DevelopmentIntegrationScopeValidator(allowedRepositoryScopeGrants());
    }

    @Produces
    @ApplicationScoped
    public ProviderRegistry providerRegistry() {
        return StaticProviderRegistry.defaultRegistry();
    }

    @Produces
    @ApplicationScoped
    public CredentialVault credentialVault(Clock clock) {
        return new InMemoryCredentialVault(clock);
    }

    @Produces
    @ApplicationScoped
    public IntegrationAuthorizer integrationAuthorizer() {
        return new EnvironmentIntegrationAuthorizer(
                Boolean.parseBoolean(env("VERICOV_DEV_AUTH_BYPASS", "false")),
                allowedOrgAccessGrants());
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

    private static Set<OrgAccessGrant> allowedOrgAccessGrants() {
        Set<OrgAccessGrant> configuredGrants = Arrays.stream(env("VERICOV_INTEGRATIONS_ALLOWED_ORG_ACCESS", "")
                        .split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(DevelopmentIntegrationComponents::parseOrgAccessGrant)
                .collect(Collectors.toUnmodifiableSet());
        OrgAccessGrant devGrant = devOrgAccessGrant();
        if (devGrant == null) {
            return configuredGrants;
        }
        return java.util.stream.Stream.concat(configuredGrants.stream(), java.util.stream.Stream.of(devGrant))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static OrgAccessGrant devOrgAccessGrant() {
        String userId = env("VERICOV_DEV_USER_ID", "");
        String tenantId = env("VERICOV_DEV_TENANT_ID", "");
        String orgId = env("VERICOV_DEV_ORG_ID", "");
        if (userId.isBlank() || tenantId.isBlank() || orgId.isBlank()) {
            return null;
        }
        return parseOrgAccessGrant(userId + ":" + tenantId + ":" + orgId);
    }

    private static OrgAccessGrant parseOrgAccessGrant(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalStateException(
                    "Integration authorization grants must use user_uuid:tenant_uuid:org_uuid");
        }
        try {
            return new OrgAccessGrant(
                    UUID.fromString(parts[0].trim()),
                    UUID.fromString(parts[1].trim()),
                    UUID.fromString(parts[2].trim()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Integration authorization grant contains an invalid UUID", exception);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static DriverManagerDataSource integrationDataSource() {
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

    private static Set<RepositoryScopeGrant> allowedRepositoryScopeGrants() {
        Set<RepositoryScopeGrant> configuredGrants = Arrays.stream(env("VERICOV_INTEGRATIONS_ALLOWED_REPOSITORY_SCOPES", "")
                        .split("[,\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(DevelopmentIntegrationComponents::parseRepositoryScopeGrant)
                .collect(Collectors.toUnmodifiableSet());
        RepositoryScopeGrant devGrant = devRepositoryScopeGrant();
        if (devGrant == null) {
            return configuredGrants;
        }
        return java.util.stream.Stream.concat(configuredGrants.stream(), java.util.stream.Stream.of(devGrant))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static RepositoryScopeGrant devRepositoryScopeGrant() {
        String tenantId = env("VERICOV_DEV_TENANT_ID", "");
        String orgId = env("VERICOV_DEV_ORG_ID", "");
        String repositoryId = env("VERICOV_DEV_REPOSITORY_ID", "");
        if (tenantId.isBlank() || orgId.isBlank() || repositoryId.isBlank()) {
            return null;
        }
        return parseRepositoryScopeGrant(tenantId + ":" + orgId + ":" + repositoryId);
    }

    private static RepositoryScopeGrant parseRepositoryScopeGrant(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalStateException(
                    "Integration repository scope grants must use tenant_uuid:org_uuid:repository_uuid");
        }
        try {
            return new RepositoryScopeGrant(
                    UUID.fromString(parts[0].trim()),
                    UUID.fromString(parts[1].trim()),
                    UUID.fromString(parts[2].trim()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Integration repository scope grant contains an invalid UUID", exception);
        }
    }

    private record EnvironmentIntegrationAuthorizer(
            boolean devAuthBypass,
            Set<OrgAccessGrant> grants) implements IntegrationAuthorizer {

        private EnvironmentIntegrationAuthorizer {
            grants = Set.copyOf(grants == null ? Set.of() : grants);
        }

        @Override
        public void requireOrgAccess(UUID requesterUserId, UUID tenantId, UUID orgId, String action) {
            if (devAuthBypass) {
                return;
            }
            if (grants.isEmpty()) {
                throw new IntegrationException("unauthorized", "Integration API authorization is not configured");
            }
            if (!grants.contains(new OrgAccessGrant(requesterUserId, tenantId, orgId))) {
                throw new IntegrationException("forbidden", "Integration API access denied");
            }
        }
    }

    private record OrgAccessGrant(
            UUID requesterUserId,
            UUID tenantId,
            UUID orgId) {
    }

    private record RepositoryScopeGrant(
            UUID tenantId,
            UUID orgId,
            UUID repositoryId) {
    }

    private record EnvironmentInternalServiceAuthorizer(
            Map<String, String> tokenHashesByService) implements InternalServiceAuthorizer {

        private EnvironmentInternalServiceAuthorizer {
            tokenHashesByService = Map.copyOf(tokenHashesByService == null ? Map.of() : tokenHashesByService);
        }

        @Override
        public String requireAuthorizedService(String serviceName, String serviceToken) {
            if (serviceName == null || serviceName.isBlank()) {
                throw new IntegrationException("unauthorized", "Service identity is required");
            }
            if (serviceToken == null || serviceToken.isBlank()) {
                throw new IntegrationException("unauthorized", "Service proof token is required");
            }
            if (tokenHashesByService.isEmpty()) {
                throw new IntegrationException("unauthorized", "Internal service authorization is not configured");
            }
            String normalizedServiceName = serviceName.trim();
            String expectedHash = tokenHashesByService.get(normalizedServiceName);
            if (expectedHash == null
                    || !MessageDigest.isEqual(sha256(serviceToken.trim()), HexFormat.of().parseHex(expectedHash))) {
                throw new IntegrationException("unauthorized", "Internal service authorization failed");
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

    private static final class DevelopmentIntegrationScopeValidator implements IntegrationScopeValidator {
        private final Set<RepositoryScopeGrant> repositoryScopeGrants;

        private DevelopmentIntegrationScopeValidator(Set<RepositoryScopeGrant> repositoryScopeGrants) {
            this.repositoryScopeGrants = Set.copyOf(repositoryScopeGrants == null ? Set.of() : repositoryScopeGrants);
        }

        @Override
        public void requireScope(UUID tenantId, UUID orgId, String scopeType, UUID scopeId) {
            if ("organization".equals(scopeType)) {
                if (orgId.equals(scopeId)) {
                    return;
                }
                throw new IntegrationException("not_found", "Integration scope not found");
            }
            if ("repository".equals(scopeType)) {
                if (repositoryScopeGrants.contains(new RepositoryScopeGrant(tenantId, orgId, scopeId))) {
                    return;
                }
                throw new IntegrationException("not_found", "Integration scope not found");
            }
            if ("component".equals(scopeType)) {
                throw new IntegrationException("not_found", "Integration scope not found");
            }
            throw new IntegrationException("validation_error", "scope_type is invalid");
        }
    }

    private static final class InMemoryCredentialVault implements CredentialVault {
        private final Clock clock;
        private final Map<String, StoredSecret> secretsByRef = new ConcurrentHashMap<>();

        private InMemoryCredentialVault(Clock clock) {
            this.clock = clock;
        }

        @Override
        public String store(UUID tenantId, UUID connectionId, String credentialKind, char[] secret) {
            String secretRef = "vault://memory/" + UUID.randomUUID();
            secretsByRef.put(secretRef, new StoredSecret(
                    tenantId,
                    connectionId,
                    credentialKind,
                    Arrays.copyOf(secret, secret.length)));
            return secretRef;
        }

        @Override
        public CredentialLease lease(UUID tenantId, UUID connectionId, String secretRef, String requestedBy) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored == null
                    || !stored.tenantId().equals(tenantId)
                    || !stored.connectionId().equals(connectionId)
                    || requestedBy == null
                    || requestedBy.isBlank()) {
                throw new IntegrationException("not_found", "Integration credential not found");
            }
            return new CredentialLease(secretRef, stored.secret(), clock.instant().plusSeconds(300));
        }

        @Override
        public void revoke(UUID tenantId, String secretRef) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored != null && stored.tenantId().equals(tenantId)) {
                secretsByRef.remove(secretRef);
            }
        }
    }

    private record StoredSecret(
            UUID tenantId,
            UUID connectionId,
            String credentialKind,
            char[] secret) {

        private StoredSecret {
            secret = Arrays.copyOf(secret, secret.length);
        }

        @Override
        public char[] secret() {
            return Arrays.copyOf(secret, secret.length);
        }
    }
}
