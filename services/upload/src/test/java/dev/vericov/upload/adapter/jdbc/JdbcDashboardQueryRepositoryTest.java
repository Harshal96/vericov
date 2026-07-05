package dev.vericov.upload.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcDashboardQueryRepositoryTest {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void overviewFiltersEveryMetricByTenant() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "repo_count", 8L,
                "active_repo_count", 6L,
                "weighted_line_coverage", new BigDecimal("81.40"),
                "total_reports", 412L,
                "active_gaps", 37L,
                "critical_gaps", 3L,
                "failing_gates", 5L));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var overview = repository.overview(TENANT_ID);

        assertEquals(8, overview.repoCount());
        assertEquals(new BigDecimal("81.40"), overview.weightedLineCoverage());
        assertTrue(dataSource.lastSql.contains("where repositories.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and status = 'failed'"));
        assertEquals(List.of(TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID),
                dataSource.parameters);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static final class RecordingDataSource implements DataSource {
        private String lastSql;
        private List<Map<String, Object>> rows = List.of();
        private final java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            lastSql = (String) args[0];
                            return preparedStatement();
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return defaultValue(method);
                    });
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setObject".equals(method.getName())) {
                            parameters.add(args[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            return resultSet(rows);
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return defaultValue(method);
                    });
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            InvocationHandler handler = new InvocationHandler() {
                private int index = -1;

                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++index < rows.size();
                        case "getLong" -> ((Number) rows.get(index).get((String) args[0])).longValue();
                        case "getBigDecimal" -> rows.get(index).get((String) args[0]);
                        case "close" -> null;
                        default -> defaultValue(method);
                    };
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[] {ResultSet.class},
                    handler);
        }

        @Override public Connection getConnection(String username, String password) { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type.equals(boolean.class)) return false;
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        return null;
    }
}
