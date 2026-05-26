package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.testresults.TestRun;
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

class JdbcTestRunRepositoryTest {
    private static final UUID TENANT_ID = UUID.fromString("0f4f478a-3fc0-45c4-b274-43a0e18850cf");
    private static final UUID REPOSITORY_ID = UUID.fromString("4d607f16-1af7-4d3b-ac38-06454cba463c");
    private static final UUID UPLOAD_ID = UUID.fromString("03ce97f7-af1c-4d65-a9a6-9f95cb4ccfc6");
    private static final UUID ARTIFACT_ID = UUID.fromString("52d0e554-4ce9-418c-96af-2c1c4cf17e3c");
    private static final UUID TEST_RUN_ID = UUID.fromString("e1a6d3f5-5f4c-476f-9807-67d5de0c4db2");
    private static final Instant NOW = Instant.parse("2026-05-23T12:00:00Z");

    @Test
    void replacesRunsForUploadAndPublishesCompletionEvent() {
        RecordingDataSource dataSource = new RecordingDataSource();
        CoverageAnalysisInput input = new CoverageAnalysisInput(
                UPLOAD_ID,
                TENANT_ID,
                REPOSITORY_ID,
                "abc123",
                "main",
                42,
                List.of());
        TestRun run = new TestRun(
                TEST_RUN_ID,
                TENANT_ID,
                REPOSITORY_ID,
                UPLOAD_ID,
                ARTIFACT_ID,
                "abc123",
                "main",
                42,
                "unit",
                0,
                "failed",
                3,
                2,
                1,
                0,
                0,
                420L,
                NOW);

        new JdbcTestRunRepository(dataSource).save(input, List.of(run), NOW);

        assertTrue(dataSource.statementContaining("delete from vericov.test_runs").parameters.containsValue(UPLOAD_ID));
        RecordedStatement insert = dataSource.statementContaining("insert into vericov.test_runs");
        assertEquals(TEST_RUN_ID, insert.parameters.get(1));
        assertEquals(TENANT_ID, insert.parameters.get(2));
        assertEquals(REPOSITORY_ID, insert.parameters.get(3));
        assertEquals(UPLOAD_ID, insert.parameters.get(4));
        assertEquals(ARTIFACT_ID, insert.parameters.get(5));
        assertEquals("unit", insert.parameters.get(9));
        assertEquals(0, insert.parameters.get(10));
        assertEquals("failed", insert.parameters.get(11));
        assertEquals(3, insert.parameters.get(12));
        assertEquals(2, insert.parameters.get(13));
        assertEquals(1, insert.parameters.get(14));
        assertEquals(420L, insert.parameters.get(17));

        RecordedStatement event = dataSource.statementContaining("'test.runs.completed'");
        assertEquals(TENANT_ID, event.parameters.get(1));
        assertEquals(UPLOAD_ID, event.parameters.get(2));
        assertEquals(1, event.parameters.get(3));
        assertEquals(3, event.parameters.get(4));
        assertEquals(2, event.parameters.get(5));
        assertEquals(1, event.parameters.get(6));
        assertEquals(0, event.parameters.get(7));
        assertEquals(0, event.parameters.get(8));
        assertEquals(420L, event.parameters.get(9));
        assertEquals(true, dataSource.committed);
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<RecordedStatement> statements = new ArrayList<>();
        private boolean committed;

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ConnectionHandler(this));
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

    private record ConnectionHandler(RecordingDataSource dataSource) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("prepareStatement".equals(method.getName())) {
                RecordedStatement statement = new RecordedStatement((String) args[0], new LinkedHashMap<>());
                dataSource.statements.add(statement);
                return Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] { PreparedStatement.class },
                        new PreparedStatementHandler(statement));
            }
            if ("commit".equals(method.getName())) {
                dataSource.committed = true;
                return null;
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
