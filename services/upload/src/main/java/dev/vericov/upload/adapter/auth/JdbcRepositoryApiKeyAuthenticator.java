package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.RepositoryApiKeyAuthenticator;
import dev.vericov.upload.domain.CreateUploadCommand;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import jakarta.json.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcRepositoryApiKeyAuthenticator implements RepositoryApiKeyAuthenticator {
    private static final String REPOSITORY_KEY_PREFIX = "vc_repo_";
    private static final String GITHUB_ACTIONS_ISSUER = "https://token.actions.githubusercontent.com";

    private final DataSource dataSource;
    private final RepositoryApiKeySecretHasher hasher;
    private final Clock clock;
    private final String runnerJwtSecret;
    private final String runnerJwtIssuer;
    private final String runnerJwtAudience;
    private final GithubActionsOidcVerifier githubActionsOidcVerifier;

    public JdbcRepositoryApiKeyAuthenticator(
            DataSource dataSource,
            RepositoryApiKeySecretHasher hasher,
            Clock clock,
            String runnerJwtSecret,
            String runnerJwtIssuer,
            String runnerJwtAudience,
            GithubActionsOidcVerifier githubActionsOidcVerifier) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.runnerJwtSecret = blankToNull(runnerJwtSecret);
        this.runnerJwtIssuer = blankToNull(runnerJwtIssuer);
        this.runnerJwtAudience = blankToNull(runnerJwtAudience);
        this.githubActionsOidcVerifier = Objects.requireNonNull(githubActionsOidcVerifier, "githubActionsOidcVerifier");
    }

    @Override
    public RepositoryApiKeyPrincipal authenticate(CreateUploadCommand command) {
        return authenticateRepositoryAccess(command.authorizationHeader(), command.repositoryId(), command.branch());
    }

    @Override
    public RepositoryApiKeyPrincipal authenticateRepositoryAccess(
            String authorizationHeader,
            UUID repositoryId,
            String branch) {
        String token = bearerToken(authorizationHeader);
        if (token.startsWith(REPOSITORY_KEY_PREFIX)) {
            return authenticateRepositoryApiKey(token, repositoryId);
        }
        if (repositoryId == null) {
            throw unauthorized();
        }
        if (token.chars().filter(character -> character == '.').count() == 2) {
            JsonObject payload = UploadJwtSupport.parseUnverifiedPayload(token);
            String issuer = UploadJwtSupport.stringClaim(payload, "iss");
            if (runnerJwtIssuer != null && runnerJwtIssuer.equals(issuer)) {
                return authenticateRunnerJwt(token, repositoryId);
            }
            if (GITHUB_ACTIONS_ISSUER.equals(issuer)) {
                return authenticateGithubActionsOidc(token, payload, repositoryId, branch);
            }
            throw unauthorized();
        }
        throw unauthorized();
    }

    private RepositoryApiKeyPrincipal authenticateRepositoryApiKey(String token, UUID repositoryId) {
        String prefix = tokenPrefix(token);
        String sql = repositoryId == null
                ? """
                select
                    api_keys.id,
                    api_keys.tenant_id,
                    api_keys.repository_id,
                    api_keys.key_hash,
                    api_keys.scopes,
                    api_keys.branch_allow_patterns,
                    api_keys.expires_at,
                    api_keys.revoked_at,
                    repositories.status
                from vericov.repository_api_keys api_keys
                join vericov.repositories repositories
                  on repositories.id = api_keys.repository_id
                where api_keys.key_prefix = ?
                """
                : """
                select
                    api_keys.id,
                    api_keys.tenant_id,
                    api_keys.repository_id,
                    api_keys.key_hash,
                    api_keys.scopes,
                    api_keys.branch_allow_patterns,
                    api_keys.expires_at,
                    api_keys.revoked_at,
                    repositories.status
                from vericov.repository_api_keys api_keys
                join vericov.repositories repositories
                  on repositories.id = api_keys.repository_id
                where api_keys.key_prefix = ?
                  and api_keys.repository_id = ?
                """;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, prefix);
            if (repositoryId != null) {
                statement.setObject(2, repositoryId);
            }
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (hasher.matches(token, resultSet.getString("key_hash"))) {
                        requireActiveCredential(resultSet, "repository");
                        RepositoryApiKeyPrincipal principal = new RepositoryApiKeyPrincipal(
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("repository_id", UUID.class),
                                resultSet.getObject("id", UUID.class),
                                Set.copyOf(stringArray(resultSet, "scopes")),
                                Set.copyOf(stringArray(resultSet, "branch_allow_patterns")));
                        markApiKeyUsed(resultSet.getObject("id", UUID.class));
                        return principal;
                    }
                }
                throw unauthorized();
            }
        } catch (SQLException exception) {
            throw new InvalidUploadException("unauthorized", "Upload credential lookup failed");
        }
    }

    private RepositoryApiKeyPrincipal authenticateRunnerJwt(String token, UUID repositoryId) {
        JsonObject payload = UploadJwtSupport.verifiedHs256Payload(
                token,
                runnerJwtSecret,
                runnerJwtIssuer,
                runnerJwtAudience,
                clock);
        UUID tokenRepositoryId = UploadJwtSupport.uuidClaim(payload, "repository_id");
        if (!repositoryId.equals(tokenRepositoryId)) {
            throw unauthorized();
        }
        UUID apiKeyId = null;
        String apiKeyIdClaim = UploadJwtSupport.stringClaim(payload, "api_key_id");
        if (apiKeyIdClaim != null) {
            try {
                apiKeyId = UUID.fromString(apiKeyIdClaim);
            } catch (IllegalArgumentException exception) {
                throw unauthorized();
            }
        }
        return new RepositoryApiKeyPrincipal(
                UploadJwtSupport.uuidClaim(payload, "tenant_id"),
                tokenRepositoryId,
                apiKeyId,
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "scopes", List.of("uploads:create"))),
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "branch_allow_patterns", List.of("*"))));
    }

    private RepositoryApiKeyPrincipal authenticateGithubActionsOidc(
            String token,
            JsonObject unverifiedPayload,
            UUID repositoryId,
            String branch) {
        String subject = UploadJwtSupport.stringClaim(unverifiedPayload, "sub");
        String audience = UploadJwtSupport.stringClaim(unverifiedPayload, "aud");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            trusts.tenant_id,
                            trusts.repository_id,
                            trusts.subject_pattern,
                            trusts.scopes,
                            trusts.branch_allow_patterns,
                            trusts.expires_at,
                            trusts.revoked_at,
                            repositories.full_name
                        from vericov.repository_ci_trusts trusts
                        join vericov.repositories repositories
                          on repositories.id = trusts.repository_id
                        where trusts.repository_id = ?
                          and trusts.provider = 'github_actions'
                          and trusts.issuer = ?
                          and trusts.audience = ?
                          and repositories.status = 'active'
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, GITHUB_ACTIONS_ISSUER);
            statement.setString(3, audience);
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requireActiveCredential(resultSet, "trust");
                    if (patternMatches(resultSet.getString("subject_pattern"), subject)) {
                        JsonObject payload = githubActionsOidcVerifier.verify(token, GITHUB_ACTIONS_ISSUER, audience);
                        if (!subject.equals(UploadJwtSupport.stringClaim(payload, "sub"))) {
                            throw unauthorized();
                        }
                        if (!repositoryClaimMatches(payload, resultSet.getString("full_name"))) {
                            throw unauthorized();
                        }
                        if (!refClaimMatchesBranch(payload, branch)) {
                            throw unauthorized();
                        }
                        return new RepositoryApiKeyPrincipal(
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getObject("repository_id", UUID.class),
                                null,
                                Set.copyOf(stringArray(resultSet, "scopes")),
                                Set.copyOf(stringArray(resultSet, "branch_allow_patterns")));
                    }
                }
                throw unauthorized();
            }
        } catch (SQLException exception) {
            throw new InvalidUploadException("unauthorized", "Upload credential lookup failed");
        }
    }

    private static boolean repositoryClaimMatches(JsonObject payload, String repositoryFullName) {
        String repository = UploadJwtSupport.stringClaim(payload, "repository");
        return repository != null && repository.equalsIgnoreCase(repositoryFullName);
    }

    private static boolean refClaimMatchesBranch(JsonObject payload, String branch) {
        String ref = UploadJwtSupport.stringClaim(payload, "ref");
        if (ref == null || branch == null || branch.isBlank()) {
            return false;
        }
        String expectedRef = "refs/heads/" + branch;
        return ref.equals(expectedRef);
    }

    private void requireActiveCredential(ResultSet resultSet, String statusColumn) throws SQLException {
        if ("repository".equals(statusColumn) && !"active".equals(resultSet.getString("status"))) {
            throw unauthorized();
        }
        if (nullableInstant(resultSet, "revoked_at") != null) {
            throw unauthorized();
        }
        Instant expiresAt = nullableInstant(resultSet, "expires_at");
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw unauthorized();
        }
    }

    private void markApiKeyUsed(UUID apiKeyId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repository_api_keys
                        set last_used_at = ?
                        where id = ?
                        """)) {
            statement.setObject(1, OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
            statement.setObject(2, apiKeyId);
            statement.executeUpdate();
        }
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new InvalidUploadException("unauthorized", "Authorization is required");
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw unauthorized();
        }
        String token = trimmed.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthorized();
        }
        return token;
    }

    private static String tokenPrefix(String token) {
        return token.substring(0, Math.min(18, token.length()));
    }

    private static boolean patternMatches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        if ("*".equals(pattern) || pattern.equals(value)) {
            return true;
        }
        if (pattern.endsWith("*")) {
            return value.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return false;
    }

    private static List<String> stringArray(ResultSet resultSet, String columnName) throws SQLException {
        java.sql.Array array = resultSet.getArray(columnName);
        if (array == null) {
            return List.of();
        }
        Object values = array.getArray();
        if (values instanceof String[] strings) {
            return List.of(strings);
        }
        Object[] objects = (Object[]) values;
        List<String> strings = new ArrayList<>(objects.length);
        for (Object object : objects) {
            strings.add(String.valueOf(object));
        }
        return List.copyOf(strings);
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        OffsetDateTime value = resultSet.getObject(columnName, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static InvalidUploadException unauthorized() {
        return new InvalidUploadException("unauthorized", "Invalid upload credential");
    }
}
