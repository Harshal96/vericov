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

    @Test
    void trendUsesDefaultBranchFallbackAndCompleteReportsOnly() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000040");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "report_id", reportId,
                "commit_sha", "abc123",
                "created_at", OffsetDateTime.parse("2026-07-04T19:00:00Z"),
                "line_pct", new BigDecimal("81.20"),
                "branch_pct", null,
                "function_pct", new BigDecimal("74.00"),
                "statement_pct", null));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var points = repository.trend(TENANT_ID, REPOSITORY_ID, null, 60);

        assertEquals(reportId, points.getFirst().reportId());
        assertEquals(new BigDecimal("81.20"), points.getFirst().linePct());
        assertTrue(dataSource.lastSql.contains("cr.status = 'complete'"));
        assertTrue(dataSource.lastSql.contains("coalesce(nullif(?, ''), r.default_branch)"));
        assertTrue(dataSource.lastSql.contains("order by created_at asc"));
        assertEquals(TENANT_ID, dataSource.parameters.get(0));
        assertEquals(REPOSITORY_ID, dataSource.parameters.get(1));
        assertEquals(null, dataSource.parameters.get(2));
        assertEquals(60, dataSource.parameters.get(3));
    }

    @Test
    void recentReportsComputeSameBranchDeltaAndJoinUploadMetadata() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        UUID uploadId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(reportRow(
                reportId,
                uploadId,
                "main",
                "complete",
                "line_delta", new BigDecimal("-0.40"),
                "ci_provider", "github_actions",
                "ci_build_url", "https://ci.example/build-1",
                "flags", new String[] {"unit", "integration"}));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var reports = repository.reports(TENANT_ID, REPOSITORY_ID, 30);

        assertEquals(reportId, reports.getFirst().report().id());
        assertEquals(new BigDecimal("-0.40"), reports.getFirst().lineDelta());
        assertEquals("github_actions", reports.getFirst().ciProvider());
        assertEquals(List.of("unit", "integration"), reports.getFirst().flags());
        assertTrue(dataSource.lastSql.contains("partition by branch order by created_at"));
        assertTrue(dataSource.lastSql.contains("where status = 'complete'"));
        assertTrue(dataSource.lastSql.contains("join vericov.uploads uploads"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID, 30), dataSource.parameters);
    }

    @Test
    void reportLookupEmbedsRepositoryAndStaysTenantScoped() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000043");
        UUID uploadId = UUID.fromString("00000000-0000-0000-0000-000000000044");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(reportRow(
                reportId,
                uploadId,
                "main",
                "complete",
                "full_name", "acme/checkout",
                "provider", "github",
                "default_branch", "main",
                "visibility", "private",
                "repo_status", "active",
                "repo_updated_at", OffsetDateTime.parse("2026-07-04T18:00:00Z")));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var report = repository.report(TENANT_ID, reportId).orElseThrow();

        assertEquals(reportId, report.report().id());
        assertEquals("acme/checkout", report.repository().fullName());
        assertTrue(dataSource.lastSql.contains("where cr.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and cr.id = ?"));
        assertEquals(List.of(TENANT_ID, reportId), dataSource.parameters);
    }

    @Test
    void reportFilesAreTenantScopedAndSortedWorstFirst() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000045");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "file_path", "src/App.java",
                "package_name", "checkout",
                "owners", new String[] {"team-payments"},
                "line_covered", 10,
                "line_total", 40,
                "branch_covered", 2,
                "branch_total", 8,
                "function_covered", 1,
                "function_total", 2,
                "statement_covered", 12,
                "statement_total", 45));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var files = repository.reportFiles(TENANT_ID, reportId);

        assertEquals("src/App.java", files.getFirst().filePath());
        assertEquals("checkout", files.getFirst().packageName());
        assertEquals(List.of("team-payments"), files.getFirst().owners());
        assertEquals(10, files.getFirst().line().covered());
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("coverage_report_id = ?"));
        assertTrue(dataSource.lastSql.contains("line_covered * 100.0 / line_total"));
        assertEquals(List.of(TENANT_ID, reportId), dataSource.parameters);
    }

    @Test
    void reportFileExistsIsTenantScoped() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000046");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row("exists", 1));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        boolean exists = repository.reportFileExists(TENANT_ID, reportId, "src/App.java");

        assertTrue(exists);
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and file_path = ?"));
        assertEquals(List.of(TENANT_ID, reportId, "src/App.java"), dataSource.parameters);
    }

    @Test
    void reportLineHitsAreOrderedByLineNumber() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000047");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(
                row("line_number", 12, "hits", 0L),
                row("line_number", 13, "hits", 4L));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var lines = repository.reportLineHits(TENANT_ID, reportId, "src/App.java");

        assertEquals(12, lines.getFirst().lineNumber());
        assertEquals(0, lines.getFirst().hits());
        assertTrue(dataSource.lastSql.contains("from vericov.coverage_line_hits"));
        assertTrue(dataSource.lastSql.contains("order by line_number"));
        assertEquals(List.of(TENANT_ID, reportId, "src/App.java"), dataSource.parameters);
    }

    @Test
    void similarReportFilePathsReuseSameBasenameMatching() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000048");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row("file_path", "src/App.java"));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var paths = repository.similarReportFilePaths(TENANT_ID, reportId, "App.java", 5);

        assertEquals(List.of("src/App.java"), paths);
        assertTrue(dataSource.lastSql.contains("file_path = ? or file_path like ?"));
        assertEquals(List.of(TENANT_ID, reportId, "App.java", "%/App.java", 5), dataSource.parameters);
    }

    @Test
    void reportComponentsReturnFlatRollupsOrderedByRiskAndOwner() {
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000049");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "component_key", "commerce/payments-api",
                "owners", new String[] {"team-payments"},
                "line_covered", 512,
                "line_total", 640,
                "branch_covered", 50,
                "branch_total", 80,
                "function_covered", 20,
                "function_total", 25,
                "statement_covered", 600,
                "statement_total", 720,
                "gap_count", 3L,
                "debt_count", 1L,
                "risk_score_total", new BigDecimal("27.50"),
                "highest_active_risk_level", "high"));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var components = repository.reportComponents(TENANT_ID, reportId);

        assertEquals("commerce/payments-api", components.getFirst().componentId());
        assertEquals("team-payments", components.getFirst().owner());
        assertEquals(new BigDecimal("27.50"), components.getFirst().riskScoreTotal());
        assertTrue(dataSource.lastSql.contains("from vericov.component_coverage_rollups"));
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("order by risk_score_total desc"));
        assertEquals(List.of(TENANT_ID, reportId), dataSource.parameters);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static Map<String, Object> reportRow(
            UUID reportId, UUID uploadId, String branch, String status, Object... extraValues) {
        Map<String, Object> row = row(
                "id", reportId,
                "upload_id", uploadId,
                "repository_id", REPOSITORY_ID,
                "commit_sha", "abc123",
                "branch", branch,
                "pull_request_number", null,
                "status", status,
                "created_at", OffsetDateTime.parse("2026-07-04T19:00:00Z"),
                "line_covered", 812,
                "line_total", 1000,
                "branch_covered", 0,
                "branch_total", 0,
                "function_covered", 74,
                "function_total", 100,
                "statement_covered", 0,
                "statement_total", 0);
        for (int index = 0; index < extraValues.length; index += 2) {
            row.put((String) extraValues[index], extraValues[index + 1]);
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
                        if ("setString".equals(method.getName())) {
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
                        case "getArray" -> {
                            Object value = rows.get(index).get((String) args[0]);
                            yield value == null ? null : array(value);
                        }
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

    private static java.sql.Array array(Object value) {
        return (java.sql.Array) Proxy.newProxyInstance(
                java.sql.Array.class.getClassLoader(),
                new Class<?>[] {java.sql.Array.class},
                (proxy, method, args) -> "getArray".equals(method.getName()) ? value : null);
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type.equals(boolean.class)) return false;
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        return null;
    }
}
