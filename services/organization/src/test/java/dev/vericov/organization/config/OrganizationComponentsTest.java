package dev.vericov.organization.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.OrganizationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class OrganizationComponentsTest {
    @Test
    void defaultsToInMemoryRepositoryWhenNoDatabaseIsConfigured() {
        Assumptions.assumeTrue(env("VERICOV_ORGANIZATION_DB_URL").isBlank() && env("SUPABASE_DB_URL").isBlank());
        OrganizationComponents components = new OrganizationComponents();

        assertInstanceOf(InMemoryOrganizationRepository.class, components.organizationRepository());
    }

    @Test
    void userPrincipalResolverRequiresJwtSecretUnlessDevBypassIsEnabled() {
        Assumptions.assumeTrue(env("VERICOV_DEV_AUTH_BYPASS").isBlank()
                && env("SUPABASE_JWT_SECRET").isBlank()
                && env("JWT_SECRET").isBlank());
        OrganizationComponents components = new OrganizationComponents();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                components::userPrincipalResolver);

        assertEquals("SUPABASE_JWT_SECRET or JWT_SECRET is required for Supabase Auth JWT validation",
                exception.getMessage());
    }

    @Test
    void internalServiceAuthorizerFailsClosedWithoutConfiguredHashes() {
        Assumptions.assumeTrue(env("VERICOV_INTERNAL_SERVICE_TOKEN_SHA256").isBlank());
        OrganizationComponents components = new OrganizationComponents();

        OrganizationException exception = assertThrows(
                OrganizationException.class,
                () -> components.internalServiceAuthorizer().requireAuthorizedService("coverage-analysis", "token"));

        assertEquals("unauthorized", exception.code());
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
