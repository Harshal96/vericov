package dev.vericov.integrations.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.integrations.application.InMemoryIntegrationRepository;
import dev.vericov.integrations.application.IntegrationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DevelopmentIntegrationComponentsTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID REQUESTER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-05-22T10:15:30Z");

    @Test
    void defaultsToInMemoryRepositoryWhenNoDatabaseIsConfigured() {
        assumeNoDatabase();
        DevelopmentIntegrationComponents components = new DevelopmentIntegrationComponents();

        assertInstanceOf(InMemoryIntegrationRepository.class, components.integrationRepository());
        assertNotNull(components.providerRegistry());
        assertNotNull(components.clock().instant());
    }

    @Test
    void inMemoryCredentialVaultStoresLeasesAndRevokesSecretsByTenantAndConnection() {
        DevelopmentIntegrationComponents components = new DevelopmentIntegrationComponents();
        var vault = components.credentialVault(Clock.fixed(NOW, ZoneOffset.UTC));
        char[] secret = "super-secret".toCharArray();

        String secretRef = vault.store(TENANT_ID, CONNECTION_ID, "github_app_private_key", secret);
        secret[0] = 'x';
        var lease = vault.lease(TENANT_ID, CONNECTION_ID, secretRef, "git-integration");

        assertArrayEquals("super-secret".toCharArray(), lease.secret());
        assertEquals(NOW.plusSeconds(300), lease.expiresAt());

        vault.revoke(TENANT_ID, secretRef);
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> vault.lease(TENANT_ID, CONNECTION_ID, secretRef, "git-integration"));
        assertEquals("not_found", exception.code());
    }

    @Test
    void defaultScopeValidatorAllowsOnlyExactOrganizationScope() {
        assumeNoDatabase();
        assumeNoRepositoryScopeGrants();
        DevelopmentIntegrationComponents components = new DevelopmentIntegrationComponents();
        var validator = components.integrationScopeValidator();

        validator.requireScope(TENANT_ID, ORG_ID, "organization", ORG_ID);
        IntegrationException missingRepository = assertThrows(
                IntegrationException.class,
                () -> validator.requireScope(TENANT_ID, ORG_ID, "repository", REPOSITORY_ID));
        IntegrationException invalidScope = assertThrows(
                IntegrationException.class,
                () -> validator.requireScope(TENANT_ID, ORG_ID, "unknown", ORG_ID));

        assertEquals("not_found", missingRepository.code());
        assertEquals("validation_error", invalidScope.code());
    }

    @Test
    void defaultAuthorizersFailClosedWhenNoGrantsOrHashesAreConfigured() {
        assumeNoAccessGrants();
        assumeNoInternalServiceHashes();
        DevelopmentIntegrationComponents components = new DevelopmentIntegrationComponents();

        IntegrationException apiException = assertThrows(
                IntegrationException.class,
                () -> components.integrationAuthorizer()
                        .requireOrgAccess(REQUESTER_ID, TENANT_ID, ORG_ID, "integrations:create"));
        IntegrationException serviceException = assertThrows(
                IntegrationException.class,
                () -> components.internalServiceAuthorizer()
                        .requireAuthorizedService("git-integration", "service-token"));

        assertEquals("unauthorized", apiException.code());
        assertEquals("unauthorized", serviceException.code());
    }

    private static void assumeNoDatabase() {
        Assumptions.assumeTrue(env("VERICOV_DATABASE_URL").isBlank() && env("SUPABASE_DB_URL").isBlank());
    }

    private static void assumeNoRepositoryScopeGrants() {
        Assumptions.assumeTrue(env("VERICOV_INTEGRATIONS_ALLOWED_REPOSITORY_SCOPES").isBlank()
                && env("VERICOV_DEV_REPOSITORY_ID").isBlank());
    }

    private static void assumeNoAccessGrants() {
        Assumptions.assumeTrue(env("VERICOV_INTEGRATIONS_ALLOWED_ORG_ACCESS").isBlank()
                && env("VERICOV_DEV_AUTH_BYPASS").isBlank()
                && env("VERICOV_DEV_USER_ID").isBlank()
                && env("VERICOV_DEV_TENANT_ID").isBlank()
                && env("VERICOV_DEV_ORG_ID").isBlank());
    }

    private static void assumeNoInternalServiceHashes() {
        Assumptions.assumeTrue(env("VERICOV_INTERNAL_SERVICE_TOKEN_SHA256").isBlank());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
