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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcDashboardQueryRepositoryTest {
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPOSITORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
        assertTrue(dataSource.lastSql.contains("evaluated_at >= now() - interval '30 days'"));
        assertEquals(List.of(TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID, TENANT_ID),
                dataSource.parameters);
    }

    @Test
    void repositoriesUseTenantScopedLatestDefaultBranchReportQuery() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "id", REPOSITORY_ID,
                "full_name", "acme/checkout",
                "provider", "github",
                "default_branch", "main",
                "visibility", "private",
                "status", "active",
                "updated_at", OffsetDateTime.parse("2026-07-04T18:00:00Z"),
                "report_id", UUID.fromString("00000000-0000-0000-0000-000000000030"),
                "commit_sha", "abc123",
                "report_created_at", OffsetDateTime.parse("2026-07-04T19:00:00Z"),
                "line_covered", 812,
                "line_total", 1000,
                "branch_covered", 0,
                "branch_total", 0,
                "function_covered", 0,
                "function_total", 0,
                "statement_covered", 0,
                "statement_total", 0,
                "line_delta", new BigDecimal("1.20"),
                "report_count", 57L,
                "active_gaps", 4L,
                "failing_gates", 1L));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var repositories = repository.repositories(TENANT_ID);

        assertEquals("acme/checkout", repositories.getFirst().fullName());
        assertEquals(new BigDecimal("1.20"), repositories.getFirst().lineDelta());
        assertTrue(dataSource.lastSql.contains("where r.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("cr.tenant_id = r.tenant_id"));
        assertTrue(dataSource.lastSql.contains("gates.tenant_id = r.tenant_id"));
        assertEquals(List.of(TENANT_ID), dataSource.parameters);
    }

    @Test
    void sparklinesUseSingleTenantScopedWindowQuery() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(
                row("repository_id", REPOSITORY_ID, "line_pct", new BigDecimal("78.10")),
                row("repository_id", REPOSITORY_ID, "line_pct", new BigDecimal("79.00")));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var sparklines = repository.sparklines(TENANT_ID, 20);

        assertEquals(List.of(new BigDecimal("78.10"), new BigDecimal("79.00")), sparklines.get(REPOSITORY_ID));
        assertTrue(dataSource.lastSql.contains("row_number() over"));
        assertTrue(dataSource.lastSql.contains("where cr.tenant_id = ?"));
        assertEquals(List.of(TENANT_ID, 20), dataSource.parameters);
    }

    @Test
    void repositoryLookupIsTenantScoped() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "id", REPOSITORY_ID,
                "full_name", "acme/checkout",
                "provider", "github",
                "default_branch", "main",
                "visibility", "private",
                "status", "active",
                "updated_at", OffsetDateTime.parse("2026-07-04T18:00:00Z")));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var repositoryDetails = repository.repository(TENANT_ID, REPOSITORY_ID).orElseThrow();

        assertEquals("acme/checkout", repositoryDetails.fullName());
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and id = ?"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID), dataSource.parameters);
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
                        if ("setInt".equals(method.getName())) {
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
                        case "getInt" -> ((Number) rows.get(index).get((String) args[0])).intValue();
                        case "getString" -> rows.get(index).get((String) args[0]);
                        case "getObject" -> getObject(rows.get(index), args);
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

    private static Object getObject(Map<String, Object> row, Object[] args) {
        return row.get((String) args[0]);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type.equals(boolean.class)) return false;
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        return null;
    }
}
