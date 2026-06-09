package dev.vericov.upload.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void readsUploadsArtifactsAndCoverageReports() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.uploadRows.add(row(
                "id", UPLOAD_ID,
                "tenant_id", TENANT_ID,
                "repository_id", REPOSITORY_ID,
                "api_key_id", null,
                "commit_sha", "abc123",
                "branch", "main",
                "pull_request_number", null,
                "ci_provider", "github_actions",
                "ci_build_id", "build-1",
                "ci_build_url", "https://ci.example/build-1",
                "flags", new String[] {"unit"},
                "component", "api",
                "package_name", null,
                "status", "processed",
                "idempotency_key", "idempotency-1",
                "accepted_at", OffsetDateTime.ofInstant(
                        Instant.parse("2026-06-05T12:00:00Z"), ZoneOffset.UTC),
                "analysis_job_id", JOB_ID));
        dataSource.artifactRows.add(row(
                "name", "coverage.lcov",
                "kind", "coverage",
                "format", "lcov",
                "content_type", "text/plain",
                "size_bytes", 7L,
                "storage_bucket", "coverage-raw",
                "storage_path", "tenant/upload/coverage.lcov",
                "sha256_hex", "a".repeat(64)));
        dataSource.reportRows.add(row(
                "upload_id", UPLOAD_ID,
                "repository_id", REPOSITORY_ID,
                "commit_sha", "abc123",
                "branch", "main",
                "pull_request_number", 42,
                "status", "complete",
                "line_covered", 8L,
                "line_total", 10L,
                "branch_covered", 3L,
                "branch_total", 4L,
                "function_covered", 2L,
                "function_total", 2L,
                "statement_covered", 9L,
                "statement_total", 12L,
                "normalized_storage_bucket", "coverage-normalized",
                "normalized_storage_path", "tenant/upload/coverage.json.gz",
                "created_at", OffsetDateTime.ofInstant(
                        Instant.parse("2026-06-05T12:01:00Z"), ZoneOffset.UTC)));
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        QueuedUpload byId = repository.findById(UPLOAD_ID).orElseThrow();
        QueuedUpload byKey = repository.findByIdempotencyKey(REPOSITORY_ID, "idempotency-1").orElseThrow();
        StoredArtifact artifact = repository.artifactsFor(UPLOAD_ID).getFirst();
        var report = repository.coverageReportFor(UPLOAD_ID).orElseThrow();

        assertEquals(UploadStatus.COMPLETED, byId.status());
        assertEquals(byId, byKey);
        assertEquals(List.of("unit"), byId.flags());
        assertEquals(ArtifactKind.COVERAGE, artifact.kind());
        assertEquals(7L, artifact.sizeBytes());
        assertEquals(42, report.pullRequestNumber());
        assertEquals(8L, report.line().covered());
        assertEquals(10L, report.line().total());
        assertEquals("coverage-normalized", report.normalizedStorageBucket());
    }

    @Test
    void returnsEmptyCollectionsWhenReadRowsDoNotExist() {
        JdbcUploadRepository repository = new JdbcUploadRepository(new RecordingDataSource());

        assertTrue(repository.findById(UPLOAD_ID).isEmpty());
        assertTrue(repository.findByIdempotencyKey(REPOSITORY_ID, "missing").isEmpty());
        assertTrue(repository.artifactsFor(UPLOAD_ID).isEmpty());
        assertTrue(repository.coverageReportFor(UPLOAD_ID).isEmpty());
    }

    @Test
    void wrapsReadFailuresWithOperationContext() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.failReads = true;
        JdbcUploadRepository repository = new JdbcUploadRepository(dataSource);

        assertTrue(assertThrows(
                IllegalStateException.class,
                () -> repository.findById(UPLOAD_ID)).getMessage().contains("Failed to load upload"));
        assertTrue(assertThrows(
                IllegalStateException.class,
                () -> repository.artifactsFor(UPLOAD_ID)).getMessage().contains("Failed to load artifacts"));
        assertTrue(assertThrows(
                IllegalStateException.class,
                () -> repository.coverageReportFor(UPLOAD_ID)).getMessage().contains("Failed to load coverage report"));
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<String> sql = new ArrayList<>();
        private final List<Map<String, Object>> uploadRows = new ArrayList<>();
        private final List<Map<String, Object>> artifactRows = new ArrayList<>();
        private final List<Map<String, Object>> reportRows = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean committed;
        private boolean rolledBack;
        private boolean failReads;

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
            InvocationHandler handler = (proxy, method, args) -> preparedStatementMethod(statementSql, method);
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    handler);
        }

        private Object preparedStatementMethod(String statementSql, Method method) throws SQLException {
            return switch (method.getName()) {
                case "executeQuery" -> {
                    if (failReads) {
                        throw new SQLException("read failed");
                    }
                    if (statementSql.contains("from vericov.uploads u")) {
                        yield resultSet(uploadRows);
                    }
                    if (statementSql.contains("from vericov.upload_artifacts")) {
                        yield resultSet(artifactRows);
                    }
                    if (statementSql.contains("from vericov.coverage_reports")) {
                        yield resultSet(reportRows);
                    }
                    yield resultSet(List.of());
                }
                case "executeUpdate" -> 1;
                case "executeBatch" -> new int[] {1};
                case "execute" -> true;
                case "addBatch", "clearParameters", "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            final int[] index = {-1};
            final boolean[] wasNull = {false};
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "next" -> ++index[0] < rows.size();
                case "getString" -> {
                    Object value = rows.get(index[0]).get((String) args[0]);
                    wasNull[0] = value == null;
                    yield value == null ? null : String.valueOf(value);
                }
                case "getLong" -> {
                    Object value = rows.get(index[0]).get((String) args[0]);
                    wasNull[0] = value == null;
                    yield value == null ? 0L : ((Number) value).longValue();
                }
                case "getInt" -> {
                    Object value = rows.get(index[0]).get((String) args[0]);
                    wasNull[0] = value == null;
                    yield value == null ? 0 : ((Number) value).intValue();
                }
                case "getObject" -> {
                    Object value = rows.get(index[0]).get((String) args[0]);
                    wasNull[0] = value == null;
                    yield value;
                }
                case "getArray" -> {
                    Object value = rows.get(index[0]).get((String) args[0]);
                    wasNull[0] = value == null;
                    yield value == null ? null : array(value);
                }
                case "wasNull" -> wasNull[0];
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[] { ResultSet.class },
                    handler);
        }

        private static Array array() {
            return array(null);
        }

        private static Array array(Object value) {
            return (Array) Proxy.newProxyInstance(
                    Array.class.getClassLoader(),
                    new Class<?>[] { Array.class },
                    (proxy, method, args) -> "getArray".equals(method.getName())
                            ? value
                            : defaultValue(method.getReturnType()));
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
