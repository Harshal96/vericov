package dev.vericov.analysis.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcAnalysisJobRepositoryTransactionTest {
    @Test
    void claimsJobAndMarksUploadProcessingInOneTransaction() {
        RecordingDataSource dataSource = new RecordingDataSource();
        var repository = new JdbcAnalysisJobRepository(dataSource);

        repository.startJob(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "worker-1",
                Instant.parse("2026-06-05T12:00:00Z"));

        assertFalse(dataSource.autoCommit);
        assertTrue(dataSource.committed);
        assertFalse(dataSource.rolledBack);
        assertTrue(dataSource.containsSql("update vericov.analysis_jobs"));
        assertTrue(dataSource.containsSql("update vericov.uploads"));
    }

    @Test
    void recordsTerminalFailureAndMarksUploadFailedInOneTransaction() {
        RecordingDataSource dataSource = new RecordingDataSource();
        var repository = new JdbcAnalysisJobRepository(dataSource);

        repository.recordTerminalFailure(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "worker-1",
                Instant.parse("2026-06-05T12:00:00Z"),
                "invalid persisted ignore rules");

        assertFalse(dataSource.autoCommit);
        assertTrue(dataSource.committed);
        assertFalse(dataSource.rolledBack);
        assertTrue(dataSource.containsSql("set status = 'failed'"));
        assertTrue(dataSource.containsSql("update vericov.uploads"));
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
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement preparedStatement(String statementSql) {
            sql.add(statementSql);
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "executeUpdate" -> 1;
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] { PreparedStatement.class },
                    handler);
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
