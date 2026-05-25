package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCoverageReportRepositoryTest {
    private static final UUID REPORT_ID = UUID.fromString("7a36b5bc-6bd2-44a2-bc8f-c886b809cf4d");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");

    @Test
    void insertsNormalizedStorageLocationWithCoverageReport() {
        RecordingDataSource dataSource = new RecordingDataSource();
        CoverageReport report = report().withNormalizedStorage(
                "coverage-normalized",
                TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz");

        new JdbcCoverageReportRepository(dataSource).save(report);

        RecordedStatement statement = dataSource.statementContaining("insert into vericov.coverage_reports");
        assertTrue(statement.sql.contains("normalized_storage_bucket"));
        assertTrue(statement.sql.contains("normalized_storage_path"));
        assertEquals("coverage-normalized", statement.parameters.get(16));
        assertEquals(
                TENANT_ID + "/" + UPLOAD_ID + "/coverage-normalized/coverage-map.json.gz",
                statement.parameters.get(17));
    }

    private static CoverageReport report() {
        return new CoverageReport(
                REPORT_ID,
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                new CoverageMetric(1, 1),
                new CoverageMetric(0, 0),
                new CoverageMetric(0, 0),
                new CoverageMetric(1, 1),
                List.of(new CoverageFileSummary(
                        "src/App.java",
                        new CoverageMetric(1, 1),
                        new CoverageMetric(0, 0),
                        new CoverageMetric(0, 0),
                        new CoverageMetric(1, 1))),
                List.of(new CoverageLineHit("src/App.java", 1, 1)),
                Instant.parse("2026-05-23T12:00:00Z"));
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<RecordedStatement> statements = new ArrayList<>();

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ConnectionHandler(statements));
        }

        private RecordedStatement statementContaining(String sqlFragment) {
            return statements.stream()
                    .filter(statement -> statement.sql.contains(sqlFragment))
                    .findFirst()
                    .orElseThrow();
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
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("unwrap is not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private record RecordedStatement(String sql, Map<Integer, Object> parameters) {
    }

    private record ConnectionHandler(List<RecordedStatement> statements) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("prepareStatement".equals(method.getName())) {
                RecordedStatement statement = new RecordedStatement((String) args[0], new LinkedHashMap<>());
                statements.add(statement);
                return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] { PreparedStatement.class },
                        new PreparedStatementHandler(statement));
            }
            return defaultValue(method.getReturnType());
        }
    }

    private record PreparedStatementHandler(RecordedStatement statement) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if (methodName.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                statement.parameters.put(index, "setNull".equals(methodName) ? null : args[1]);
                return null;
            }
            if ("executeBatch".equals(methodName)) {
                return new int[] { 1 };
            }
            if ("executeUpdate".equals(methodName)) {
                return 1;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0.0F;
        }
        if (returnType == Double.TYPE) {
            return 0.0D;
        }
        return null;
    }
}
