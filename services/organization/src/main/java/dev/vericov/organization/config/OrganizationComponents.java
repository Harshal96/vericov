package dev.vericov.organization.config;

import dev.vericov.organization.adapter.auth.SupabaseJwtUserPrincipalResolver;
import dev.vericov.organization.adapter.jdbc.DriverManagerDataSource;
import dev.vericov.organization.adapter.jdbc.JdbcOrganizationRepository;
import dev.vericov.organization.application.InMemoryOrganizationRepository;
import dev.vericov.organization.application.OrganizationApplicationService;
import dev.vericov.organization.application.OrganizationException;
import dev.vericov.organization.application.port.OrganizationRepository;
import dev.vericov.organization.application.port.UserPrincipalResolver;
import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;
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
        String jdbcUrl = env("VERICOV_ORGANIZATION_DB_URL", env("SUPABASE_DB_URL", ""));
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
        String jwtSecret = env("SUPABASE_JWT_SECRET", env("JWT_SECRET", ""));
        if (jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_JWT_SECRET or JWT_SECRET is required for Supabase Auth JWT validation");
        }
        return new SupabaseJwtUserPrincipalResolver(
                jwtSecret,
                env("SUPABASE_JWT_ISSUER", env("GOTRUE_JWT_ISSUER", "http://localhost:8000/auth/v1")),
                env("SUPABASE_JWT_AUDIENCE", env("GOTRUE_JWT_AUD", "authenticated")),
                Clock.systemUTC());
    }

    private static DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(
                jdbcUrl,
                requiredDbEnv("VERICOV_ORGANIZATION_DB_USER", "SUPABASE_DB_USER"),
                requiredDbEnv("VERICOV_ORGANIZATION_DB_PASSWORD", "SUPABASE_DB_PASSWORD"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when organization JDBC is configured");
        }
        return value;
    }

    private static String requiredDbEnv(String primaryName, String fallbackName) {
        String primary = System.getenv(primaryName);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return requiredEnv(fallbackName);
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
