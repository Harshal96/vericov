package dev.vericov.upload.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.UploadStatus;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcUploadRepositoryTest {
    private static final UUID UPLOAD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPOSITORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JOB_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void savesUploadArtifactsJobEventAndQueueMessageInOneTransaction() {
        RecordingDataSource dataSource = new RecordingDataSource();
        var repository = new JdbcUploadRepository(dataSource);
        var upload = new QueuedUpload(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                Optional.empty(),
                "abc123",
                "main",
                42,
                "github_actions",
                "build-1",
                "https://ci.example/build-1",
                List.of("unit"),
                Optional.of("api"),
                Optional.empty(),
                UploadStatus.QUEUED,
                "idempotency-1",
                Instant.parse("2026-06-05T12:00:00Z"),
                Optional.of(JOB_ID));
        var artifact = new StoredArtifact(
                "coverage.lcov",
                ArtifactKind.COVERAGE,
                "lcov",
                "text/plain",
                7,
                "coverage-raw",
                TENANT_ID + "/" + UPLOAD_ID + "/coverage/coverage.lcov",
                "a".repeat(64));

        repository.save(upload, List.of(artifact));

        assertFalse(dataSource.autoCommit);
        assertTrue(dataSource.committed);
        assertFalse(dataSource.rolledBack);
        assertTrue(dataSource.containsSql("insert into vericov.uploads"));
        assertTrue(dataSource.containsSql("insert into vericov.upload_artifacts"));
        assertTrue(dataSource.containsSql("insert into vericov.analysis_jobs"));
        assertTrue(dataSource.containsSql("insert into vericov.upload_events"));
        assertTrue(dataSource.containsSql("select vericov.enqueue_coverage_analysis_job"));
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<String> sql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean committed;
        private boolean rolledBack;

        private boolean containsSql(String fragment) {
            return sql.stream().anyMatch(value -> value.contains(fragment));
        }

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
                case "setAutoCommit" -> {
                    autoCommit = (boolean) args[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "createArrayOf" -> array();
                case "close" -> null;
                case "isClosed" -> false;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement preparedStatement(String statementSql) {
            sql.add(statementSql);
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "executeUpdate" -> 1;
                case "executeBatch" -> new int[] {1};
                case "execute" -> true;
                case "addBatch", "clearParameters", "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    handler);
        }

        private static Array array() {
            return (Array) Proxy.newProxyInstance(
                    Array.class.getClassLoader(),
                    new Class<?>[] { Array.class },
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
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
}
