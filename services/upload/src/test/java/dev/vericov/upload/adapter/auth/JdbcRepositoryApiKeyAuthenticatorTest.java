package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.domain.RepositoryApiKeyPrincipal;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcRepositoryApiKeyAuthenticatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
    private static final String RUNNER_SECRET = "runner-secret";
    private static final String RUNNER_ISSUER = "vericov-upload";
    private static final String RUNNER_AUDIENCE = "vericov-runner-upload";
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID API_KEY_ID = UUID.fromString("9f66fbf9-512e-4de1-94c2-dfca2c18e72b");
    private static final String REPOSITORY_FULL_NAME = "acme/payments-api";

    @Test
    void authenticatesRepositoryApiKeyAndMarksKeyUsed() {
        String token = "vc_repo_0123456789abcdef";
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.repositoryApiKeyRows.add(repositoryApiKeyRow(token, "active", null, null));
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(dataSource, new StubGithubActionsOidcVerifier(null));

        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess("Bearer " + token, null, "main");

        assertEquals(TENANT_ID, principal.tenantId());
        assertEquals(REPOSITORY_ID, principal.repositoryId());
        assertEquals(API_KEY_ID, principal.apiKeyId());
        assertEquals(Set.of("uploads:create", "uploads:read"), principal.scopes());
        assertEquals(Set.of("main", "release/*"), principal.allowedBranches());
        assertEquals(1, dataSource.markKeyUsedCount);
        assertEquals(API_KEY_ID, dataSource.lastMarkedApiKeyId);
    }

    @Test
    void rejectsRepositoryApiKeysWhenHashStatusRevocationOrExpiryDoNotAllowAccess() {
        String token = "vc_repo_0123456789abcdef";

        InvalidUploadException hashMismatch = assertThrows(
                InvalidUploadException.class,
                () -> authenticatorWithRepositoryRows(repositoryApiKeyRow("vc_repo_other_token", "active", null, null))
                        .authenticateRepositoryAccess("Bearer " + token, REPOSITORY_ID, "main"));
        InvalidUploadException inactive = assertThrows(
                InvalidUploadException.class,
                () -> authenticatorWithRepositoryRows(repositoryApiKeyRow(token, "disabled", null, null))
                        .authenticateRepositoryAccess("Bearer " + token, REPOSITORY_ID, "main"));
        InvalidUploadException revoked = assertThrows(
                InvalidUploadException.class,
                () -> authenticatorWithRepositoryRows(repositoryApiKeyRow(
                                token,
                                "active",
                                OffsetDateTime.ofInstant(CLOCK.instant().minusSeconds(1), ZoneOffset.UTC),
                                null))
                        .authenticateRepositoryAccess("Bearer " + token, REPOSITORY_ID, "main"));
        InvalidUploadException expired = assertThrows(
                InvalidUploadException.class,
                () -> authenticatorWithRepositoryRows(repositoryApiKeyRow(
                                token,
                                "active",
                                null,
                                OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)))
                        .authenticateRepositoryAccess("Bearer " + token, REPOSITORY_ID, "main"));

        assertEquals("unauthorized", hashMismatch.code());
        assertEquals("unauthorized", inactive.code());
        assertEquals("unauthorized", revoked.code());
        assertEquals("unauthorized", expired.code());
    }

    @Test
    void mapsRepositoryLookupFailuresToUnauthorizedErrors() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.failRepositoryKeyQuery = true;
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(dataSource, new StubGithubActionsOidcVerifier(null));

        InvalidUploadException exception = assertThrows(
                InvalidUploadException.class,
                () -> authenticator.authenticateRepositoryAccess("Bearer vc_repo_0123456789abcdef", REPOSITORY_ID, "main"));

        assertEquals("unauthorized", exception.code());
        assertEquals("Upload credential lookup failed", exception.getMessage());
    }

    @Test
    void authenticatesRunnerJwtWithDefaultScopesAndBranches() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(dataSource, new StubGithubActionsOidcVerifier(null));
        JsonObject payload = Json.createObjectBuilder()
                .add("iss", RUNNER_ISSUER)
                .add("aud", RUNNER_AUDIENCE)
                .add("sub", "repository:" + REPOSITORY_ID)
                .add("tenant_id", TENANT_ID.toString())
                .add("repository_id", REPOSITORY_ID.toString())
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .add("nbf", CLOCK.instant().minusSeconds(5).getEpochSecond())
                .build();

        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(
                "Bearer " + hs256Token(payload),
                REPOSITORY_ID,
                "main");

        assertEquals(TENANT_ID, principal.tenantId());
        assertEquals(REPOSITORY_ID, principal.repositoryId());
        assertNull(principal.apiKeyId());
        assertEquals(Set.of("uploads:create"), principal.scopes());
        assertEquals(Set.of("*"), principal.allowedBranches());
    }

    @Test
    void rejectsRunnerJwtWithRepositoryMismatchOrInvalidApiKeyId() {
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(new RecordingDataSource(), new StubGithubActionsOidcVerifier(null));
        JsonObject wrongRepository = Json.createObjectBuilder()
                .add("iss", RUNNER_ISSUER)
                .add("aud", RUNNER_AUDIENCE)
                .add("tenant_id", TENANT_ID.toString())
                .add("repository_id", UUID.randomUUID().toString())
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build();
        JsonObject invalidApiKeyId = Json.createObjectBuilder()
                .add("iss", RUNNER_ISSUER)
                .add("aud", RUNNER_AUDIENCE)
                .add("tenant_id", TENANT_ID.toString())
                .add("repository_id", REPOSITORY_ID.toString())
                .add("api_key_id", "not-a-uuid")
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build();

        InvalidUploadException repositoryMismatch = assertThrows(
                InvalidUploadException.class,
                () -> authenticator.authenticateRepositoryAccess("Bearer " + hs256Token(wrongRepository), REPOSITORY_ID, "main"));
        InvalidUploadException badApiKeyId = assertThrows(
                InvalidUploadException.class,
                () -> authenticator.authenticateRepositoryAccess("Bearer " + hs256Token(invalidApiKeyId), REPOSITORY_ID, "main"));

        assertEquals("unauthorized", repositoryMismatch.code());
        assertEquals("unauthorized", badApiKeyId.code());
    }

    @Test
    void authenticatesGithubActionsOidcTrustWhenClaimsMatch() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.trustRows.add(trustRow("repo:acme/payments-api:*", null, null));
        JsonObject verifiedPayload = Json.createObjectBuilder()
                .add("iss", "https://token.actions.githubusercontent.com")
                .add("aud", "vericov-upload")
                .add("sub", "repo:acme/payments-api:ref:refs/heads/main")
                .add("repository", REPOSITORY_FULL_NAME)
                .add("ref", "refs/heads/main")
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build();
        StubGithubActionsOidcVerifier verifier = new StubGithubActionsOidcVerifier(verifiedPayload);
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(dataSource, verifier);

        RepositoryApiKeyPrincipal principal = authenticator.authenticateRepositoryAccess(
                "Bearer " + unsignedGithubToken("repo:acme/payments-api:ref:refs/heads/main", "vericov-upload"),
                REPOSITORY_ID,
                "main");

        assertEquals(TENANT_ID, principal.tenantId());
        assertEquals(REPOSITORY_ID, principal.repositoryId());
        assertNull(principal.apiKeyId());
        assertEquals(Set.of("uploads:create", "uploads:read"), principal.scopes());
        assertEquals(Set.of("main", "release/*"), principal.allowedBranches());
        assertEquals("vericov-upload", verifier.lastAudience);
    }

    @Test
    void rejectsGithubActionsOidcWhenClaimsDoNotMatchOrLookupFails() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.trustRows.add(trustRow("repo:acme/payments-api:*", null, null));
        StubGithubActionsOidcVerifier wrongRepositoryVerifier = new StubGithubActionsOidcVerifier(Json.createObjectBuilder()
                .add("iss", "https://token.actions.githubusercontent.com")
                .add("aud", "vericov-upload")
                .add("sub", "repo:acme/payments-api:ref:refs/heads/main")
                .add("repository", "acme/other-repo")
                .add("ref", "refs/heads/main")
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build());
        JdbcRepositoryApiKeyAuthenticator repositoryMismatch = authenticator(dataSource, wrongRepositoryVerifier);

        InvalidUploadException wrongRepository = assertThrows(
                InvalidUploadException.class,
                () -> repositoryMismatch.authenticateRepositoryAccess(
                        "Bearer " + unsignedGithubToken("repo:acme/payments-api:ref:refs/heads/main", "vericov-upload"),
                        REPOSITORY_ID,
                        "main"));
        assertEquals("unauthorized", wrongRepository.code());

        RecordingDataSource branchDataSource = new RecordingDataSource();
        branchDataSource.trustRows.add(trustRow("repo:acme/payments-api:*", null, null));
        StubGithubActionsOidcVerifier wrongBranchVerifier = new StubGithubActionsOidcVerifier(Json.createObjectBuilder()
                .add("iss", "https://token.actions.githubusercontent.com")
                .add("aud", "vericov-upload")
                .add("sub", "repo:acme/payments-api:ref:refs/heads/main")
                .add("repository", REPOSITORY_FULL_NAME)
                .add("ref", "refs/heads/release")
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build());
        JdbcRepositoryApiKeyAuthenticator branchMismatch = authenticator(branchDataSource, wrongBranchVerifier);

        InvalidUploadException wrongBranch = assertThrows(
                InvalidUploadException.class,
                () -> branchMismatch.authenticateRepositoryAccess(
                        "Bearer " + unsignedGithubToken("repo:acme/payments-api:ref:refs/heads/main", "vericov-upload"),
                        REPOSITORY_ID,
                        "main"));
        assertEquals("unauthorized", wrongBranch.code());

        RecordingDataSource failingDataSource = new RecordingDataSource();
        failingDataSource.failTrustQuery = true;
        JdbcRepositoryApiKeyAuthenticator failingAuthenticator = authenticator(
                failingDataSource,
                new StubGithubActionsOidcVerifier(assertInstanceOf(JsonObject.class, Json.createObjectBuilder()
                        .add("iss", "https://token.actions.githubusercontent.com")
                        .add("aud", "vericov-upload")
                        .add("sub", "repo:acme/payments-api:ref:refs/heads/main")
                        .add("repository", REPOSITORY_FULL_NAME)
                        .add("ref", "refs/heads/main")
                        .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                        .build())));

        InvalidUploadException lookupFailure = assertThrows(
                InvalidUploadException.class,
                () -> failingAuthenticator.authenticateRepositoryAccess(
                        "Bearer " + unsignedGithubToken("repo:acme/payments-api:ref:refs/heads/main", "vericov-upload"),
                        REPOSITORY_ID,
                        "main"));
        assertEquals("unauthorized", lookupFailure.code());
        assertEquals("Upload credential lookup failed", lookupFailure.getMessage());
    }

    @Test
    void rejectsMissingAuthorizationAndUnknownBearerTokens() {
        JdbcRepositoryApiKeyAuthenticator authenticator = authenticator(new RecordingDataSource(), new StubGithubActionsOidcVerifier(null));

        InvalidUploadException missing = assertThrows(
                InvalidUploadException.class,
                () -> authenticator.authenticateRepositoryAccess(" ", REPOSITORY_ID, "main"));
        InvalidUploadException unknownToken = assertThrows(
                InvalidUploadException.class,
                () -> authenticator.authenticateRepositoryAccess("Bearer opaque-token", REPOSITORY_ID, "main"));

        assertEquals("unauthorized", missing.code());
        assertEquals("Authorization is required", missing.getMessage());
        assertEquals("unauthorized", unknownToken.code());
    }

    private static JdbcRepositoryApiKeyAuthenticator authenticator(
            RecordingDataSource dataSource,
            GithubActionsOidcVerifier githubActionsOidcVerifier) {
        return new JdbcRepositoryApiKeyAuthenticator(
                dataSource,
                new RepositoryApiKeySecretHasher("pepper"),
                CLOCK,
                RUNNER_SECRET,
                RUNNER_ISSUER,
                RUNNER_AUDIENCE,
                githubActionsOidcVerifier);
    }

    private static JdbcRepositoryApiKeyAuthenticator authenticatorWithRepositoryRows(Map<String, Object> row) {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.repositoryApiKeyRows.add(row);
        return authenticator(dataSource, new StubGithubActionsOidcVerifier(null));
    }

    private static Map<String, Object> repositoryApiKeyRow(
            String token,
            String status,
            OffsetDateTime revokedAt,
            OffsetDateTime expiresAt) {
        return row(
                "id", API_KEY_ID,
                "tenant_id", TENANT_ID,
                "repository_id", REPOSITORY_ID,
                "key_hash", new RepositoryApiKeySecretHasher("pepper").hash(token),
                "scopes", new String[] { "uploads:create", "uploads:read" },
                "branch_allow_patterns", new String[] { "main", "release/*" },
                "expires_at", expiresAt,
                "revoked_at", revokedAt,
                "status", status);
    }

    private static Map<String, Object> trustRow(
            String subjectPattern,
            OffsetDateTime revokedAt,
            OffsetDateTime expiresAt) {
        return row(
                "tenant_id", TENANT_ID,
                "repository_id", REPOSITORY_ID,
                "subject_pattern", subjectPattern,
                "scopes", new String[] { "uploads:create", "uploads:read" },
                "branch_allow_patterns", new String[] { "main", "release/*" },
                "expires_at", expiresAt,
                "revoked_at", revokedAt,
                "full_name", REPOSITORY_FULL_NAME);
    }

    private static String hs256Token(JsonObject payload) {
        String encodedHeader = encodeJson(Json.createObjectBuilder()
                .add("alg", "HS256")
                .add("typ", "JWT")
                .build());
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(RUNNER_SECRET, signingInput));
        return signingInput + "." + signature;
    }

    private static String unsignedGithubToken(String subject, String audience) {
        String header = encodeJson(Json.createObjectBuilder()
                .add("alg", "RS256")
                .add("typ", "JWT")
                .add("kid", "github-test")
                .build());
        String payload = encodeJson(Json.createObjectBuilder()
                .add("iss", "https://token.actions.githubusercontent.com")
                .add("aud", audience)
                .add("sub", subject)
                .add("exp", CLOCK.instant().plusSeconds(60).getEpochSecond())
                .build());
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1});
    }

    private static String encodeJson(JsonObject object) {
        StringWriter writer = new StringWriter();
        Json.createWriter(writer).writeObject(object);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(String secret, String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static final class StubGithubActionsOidcVerifier extends GithubActionsOidcVerifier {
        private final JsonObject payload;
        private String lastAudience;

        private StubGithubActionsOidcVerifier(JsonObject payload) {
            super(URI.create("https://example.test/jwks"), CLOCK);
            this.payload = payload;
        }

        @Override
        public JsonObject verify(String token, String issuer, String audience) {
            this.lastAudience = audience;
            if (payload == null) {
                throw new InvalidUploadException("unauthorized", "Invalid upload credential");
            }
            return payload;
        }
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<Map<String, Object>> repositoryApiKeyRows = new ArrayList<>();
        private final List<Map<String, Object>> trustRows = new ArrayList<>();
        private boolean failRepositoryKeyQuery;
        private boolean failTrustQuery;
        private int markKeyUsedCount;
        private UUID lastMarkedApiKeyId;

        @Override
        public Connection getConnection() {
            InvocationHandler handler = (proxy, method, args) -> connectionMethod(method, args);
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    handler);
        }

        private Object connectionMethod(Method method, Object[] args) {
            return switch (method.getName()) {
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "close" -> null;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement preparedStatement(String sql) {
            Map<Integer, Object> parameters = new LinkedHashMap<>();
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "setString", "setObject" -> {
                    parameters.put((Integer) args[0], args[1]);
                    yield null;
                }
                case "executeQuery" -> executeQuery(sql);
                case "executeUpdate" -> executeUpdate(sql, parameters);
                case "close", "clearParameters" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    handler);
        }

        private ResultSet executeQuery(String sql) throws SQLException {
            if (sql.contains("from vericov.repository_api_keys")) {
                if (failRepositoryKeyQuery) {
                    throw new SQLException("repository lookup failed");
                }
                return resultSet(repositoryApiKeyRows);
            }
            if (sql.contains("from vericov.repository_ci_trusts")) {
                if (failTrustQuery) {
                    throw new SQLException("trust lookup failed");
                }
                return resultSet(trustRows);
            }
            throw new SQLException("Unexpected SQL: " + sql);
        }

        private int executeUpdate(String sql, Map<Integer, Object> parameters) {
            if (sql.contains("update vericov.repository_api_keys")) {
                markKeyUsedCount++;
                lastMarkedApiKeyId = (UUID) parameters.get(2);
                return 1;
            }
            return 0;
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int index = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++index < rows.size();
                        case "getString" -> {
                            Object value = rows.get(index).get(args[0]);
                            yield value == null ? null : String.valueOf(value);
                        }
                        case "getObject" -> getObject(rows.get(index), args);
                        case "getArray" -> array(rows.get(index).get(args[0]));
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[] { ResultSet.class },
                    handler);
        }

        private static Object getObject(Map<String, Object> row, Object[] args) {
            Object value = row.get(args[0]);
            if (args.length == 2 && args[1] instanceof Class<?> type && value != null) {
                return type.cast(value);
            }
            return value;
        }

        private static Array array(Object value) {
            return (Array) Proxy.newProxyInstance(
                    Array.class.getClassLoader(),
                    new Class<?>[] { Array.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getArray" -> value;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
