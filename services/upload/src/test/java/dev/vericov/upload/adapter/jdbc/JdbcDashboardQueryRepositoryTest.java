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
import java.util.Arrays;
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

    @Test
    void tenantWideGapsUseLatestCompleteDefaultBranchReports() {
        UUID gapId = UUID.fromString("00000000-0000-0000-0000-000000000050");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000051");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(gapRow(gapId, reportId));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var gaps = repository.gaps(TENANT_ID, "active", 100);

        assertEquals(gapId, gaps.getFirst().id());
        assertEquals(reportId, gaps.getFirst().coverageReportId());
        assertEquals(REPOSITORY_ID, gaps.getFirst().repositoryId());
        assertTrue(dataSource.lastSql.contains("latest_default_reports"));
        assertTrue(dataSource.lastSql.contains("reports.branch = repositories.default_branch"));
        assertTrue(dataSource.lastSql.contains("reports.status = 'complete'"));
        assertTrue(dataSource.lastSql.contains("repositories.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("gaps.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("gaps.status = ?"));
        assertTrue(dataSource.lastSql.contains("order by gaps.risk_score desc, gaps.id"));
        assertEquals(List.of(TENANT_ID, TENANT_ID, "active", "active", 100), dataSource.parameters);
    }

    @Test
    void repositoryGapsAllowBlankStatusAndUseSameLatestReportSemantics() {
        UUID gapId = UUID.fromString("00000000-0000-0000-0000-000000000052");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000053");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(gapRow(gapId, reportId));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var gaps = repository.repositoryGaps(TENANT_ID, REPOSITORY_ID, null, 200);

        assertEquals(gapId, gaps.getFirst().id());
        assertTrue(dataSource.lastSql.contains("repositories.id = ?"));
        assertTrue(dataSource.lastSql.contains("reports.branch = repositories.default_branch"));
        assertTrue(dataSource.lastSql.contains("reports.status = 'complete'"));
        assertTrue(dataSource.lastSql.contains("(?::text is null or gaps.status = ?)"));
        assertEquals(Arrays.asList(TENANT_ID, REPOSITORY_ID, TENANT_ID, REPOSITORY_ID, null, null, 200),
                dataSource.parameters);
    }

    @Test
    void repositoryGapCountsUseActiveLatestDefaultBranchReportPopulation() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(
                row("risk_level", "critical", "n", 1L),
                row("risk_level", "high", "n", 4L),
                row("risk_level", "medium", "n", 7L),
                row("risk_level", "low", "n", 2L));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var counts = repository.repositoryGapCounts(TENANT_ID, REPOSITORY_ID);

        assertEquals(1, counts.critical());
        assertEquals(4, counts.high());
        assertEquals(7, counts.medium());
        assertEquals(2, counts.low());
        assertTrue(dataSource.lastSql.contains("latest_default_report"));
        assertTrue(dataSource.lastSql.contains("reports.branch = repositories.default_branch"));
        assertTrue(dataSource.lastSql.contains("gaps.status = 'active'"));
        assertTrue(dataSource.lastSql.contains("group by gaps.risk_level"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID, TENANT_ID, REPOSITORY_ID), dataSource.parameters);
    }

    @Test
    void gateConfigsIncludeInactivePoliciesAndStayTenantScoped() {
        UUID configId = UUID.fromString("00000000-0000-0000-0000-000000000060");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(row(
                "id", configId,
                "name", "project line floor",
                "gate_type", "project_coverage",
                "metric", "line",
                "threshold", new BigDecimal("80.0"),
                "max_drop", null,
                "blocking", true,
                "status", "disabled"));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var configs = repository.gateConfigs(TENANT_ID, REPOSITORY_ID);

        assertEquals(configId, configs.getFirst().id());
        assertEquals("disabled", configs.getFirst().status());
        assertTrue(dataSource.lastSql.contains("from vericov.repository_gate_configurations"));
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and repository_id = ?"));
        assertTrue(dataSource.lastSql.contains("order by blocking desc, name"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID), dataSource.parameters);
    }

    @Test
    void repositoryGateEvaluationsAreNewestFirstAndLimited() {
        UUID evaluationId = UUID.fromString("00000000-0000-0000-0000-000000000061");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000062");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(gateEvaluationRow(evaluationId, reportId));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var evaluations = repository.repositoryGateEvaluations(TENANT_ID, REPOSITORY_ID, 60);

        assertEquals(evaluationId, evaluations.getFirst().id());
        assertEquals(reportId, evaluations.getFirst().coverageReportId());
        assertTrue(dataSource.lastSql.contains("from vericov.gate_evaluations"));
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and repository_id = ?"));
        assertTrue(dataSource.lastSql.contains("order by evaluated_at desc"));
        assertTrue(dataSource.lastSql.contains("limit ?"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID, 60), dataSource.parameters);
    }

    @Test
    void reportGatesAreTenantScopedAndBlockingFirst() {
        UUID evaluationId = UUID.fromString("00000000-0000-0000-0000-000000000063");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000064");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(gateEvaluationRow(evaluationId, reportId));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var gates = repository.reportGates(TENANT_ID, reportId);

        assertEquals("project line floor", gates.getFirst().gateName());
        assertEquals(new BigDecimal("78.9"), gates.getFirst().actual());
        assertTrue(dataSource.lastSql.contains("from vericov.gate_evaluations"));
        assertTrue(dataSource.lastSql.contains("where tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and coverage_report_id = ?"));
        assertTrue(dataSource.lastSql.contains("order by blocking desc, gate_name"));
        assertEquals(List.of(TENANT_ID, reportId), dataSource.parameters);
    }

    @Test
    void pullRequestDiffsDedupeLatestPerNumberAndOrderNewestFirst() {
        UUID diffId = UUID.fromString("00000000-0000-0000-0000-000000000070");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000071");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rows = List.of(prDiffRow(diffId, reportId));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var diffs = repository.pullRequestDiffs(TENANT_ID, REPOSITORY_ID, 40);

        assertEquals(diffId, diffs.getFirst().id());
        assertEquals(481, diffs.getFirst().pullRequestNumber());
        assertEquals(reportId, diffs.getFirst().coverageReportId());
        assertEquals(new BigDecimal("81.20"), diffs.getFirst().projectLinePct());
        assertTrue(dataSource.lastSql.contains("distinct on (d.pull_request_number)"));
        assertTrue(dataSource.lastSql.contains("where d.tenant_id = ?"));
        assertTrue(dataSource.lastSql.contains("and d.repository_id = ?"));
        assertTrue(dataSource.lastSql.contains("order by d.pull_request_number, d.created_at desc"));
        assertTrue(dataSource.lastSql.contains(") latest"));
        assertTrue(dataSource.lastSql.contains("order by created_at desc"));
        assertEquals(List.of(TENANT_ID, REPOSITORY_ID, 40), dataSource.parameters);
    }

    @Test
    void pullRequestDiffLoadsDiffThenNestedFilesInOrder() {
        UUID diffId = UUID.fromString("00000000-0000-0000-0000-000000000072");
        UUID reportId = UUID.fromString("00000000-0000-0000-0000-000000000073");
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rowsQueue = new java.util.ArrayDeque<>(List.of(
                List.of(prDiffRow(diffId, reportId)),
                List.of(prDiffFileRow("src/App.java", "modified"))));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var details = repository.pullRequestDiff(TENANT_ID, diffId).orElseThrow();

        assertEquals(diffId, details.diff().id());
        assertEquals(reportId, details.diff().coverageReportId());
        assertEquals(1, details.files().size());
        assertEquals("src/App.java", details.files().getFirst().filePath());
        assertEquals("modified", details.files().getFirst().changeStatus());
        assertEquals(2, dataSource.sqlLog.size());
        assertTrue(dataSource.sqlLog.get(0).contains("from vericov.pull_request_coverage_diffs d"));
        assertTrue(dataSource.sqlLog.get(0).contains("where d.tenant_id = ?"));
        assertTrue(dataSource.sqlLog.get(0).contains("and d.id = ?"));
        assertTrue(dataSource.sqlLog.get(1).contains("from vericov.pull_request_coverage_diff_files"));
        assertTrue(dataSource.sqlLog.get(1).contains("where tenant_id = ?"));
        assertTrue(dataSource.sqlLog.get(1).contains("and pr_diff_id = ?"));
        assertEquals(List.of(TENANT_ID, diffId), dataSource.parameterLog.get(0));
        assertEquals(List.of(TENANT_ID, diffId), dataSource.parameterLog.get(1));
    }

    @Test
    void pullRequestDiffReturnsEmptyWhenDiffMissing() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.rowsQueue = new java.util.ArrayDeque<>(List.of(List.of()));
        JdbcDashboardQueryRepository repository = new JdbcDashboardQueryRepository(dataSource);

        var details = repository.pullRequestDiff(TENANT_ID, UUID.fromString("00000000-0000-0000-0000-000000000074"));

        assertTrue(details.isEmpty());
    }

    private static Map<String, Object> prDiffRow(UUID diffId, UUID reportId) {
        return row(
                "id", diffId,
                "pull_request_number", 481,
                "base_sha", "base123",
                "head_sha", "head456",
                "status", "complete",
                "patch_line_covered", 34,
                "patch_line_total", 40,
                "newly_missed_line_count", 6,
                "lost_coverage_line_count", 2,
                "created_at", OffsetDateTime.parse("2026-07-04T19:00:00Z"),
                "coverage_report_id", reportId,
                "project_line_pct", new BigDecimal("81.20"));
    }

    private static Map<String, Object> prDiffFileRow(String filePath, String changeStatus) {
        return row(
                "file_path", filePath,
                "change_status", changeStatus,
                "patch_line_covered", 8,
                "patch_line_total", 12,
                "newly_missed_line_count", 4,
                "lost_coverage_line_count", 0);
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

    private static Map<String, Object> gapRow(UUID gapId, UUID reportId) {
        return row(
                "id", gapId,
                "coverage_report_id", reportId,
                "repository_id", REPOSITORY_ID,
                "file_path", "src/App.java",
                "target_type", "function",
                "line_start", 12,
                "line_end", 34,
                "symbol_name", "retryPayment",
                "reason_code", "uncovered_branch",
                "explanation", "No test exercises the retry branch.",
                "confidence", "high",
                "risk_score", new BigDecimal("8.5"),
                "risk_level", "critical",
                "owners", new String[] {"team-payments"},
                "next_action", "write_test",
                "status", "active",
                "commit_sha", "abc123",
                "pull_request_number", null);
    }

    private static Map<String, Object> gateEvaluationRow(UUID evaluationId, UUID reportId) {
        return row(
                "id", evaluationId,
                "gate_name", "project line floor",
                "gate_type", "project_coverage",
                "metric", "line",
                "threshold", new BigDecimal("80.0"),
                "actual", new BigDecimal("78.9"),
                "status", "failed",
                "blocking", true,
                "commit_sha", "abc123",
                "branch", "main",
                "pull_request_number", null,
                "evaluated_at", OffsetDateTime.parse("2026-07-04T20:00:00Z"),
                "coverage_report_id", reportId);
    }

    private static final class RecordingDataSource implements DataSource {
        private String lastSql;
        private List<Map<String, Object>> rows = List.of();
        private java.util.Queue<List<Map<String, Object>>> rowsQueue;
        private final List<String> sqlLog = new java.util.ArrayList<>();
        private final List<List<Object>> parameterLog = new java.util.ArrayList<>();
        private final java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            lastSql = (String) args[0];
                            sqlLog.add(lastSql);
                            List<Map<String, Object>> statementRows =
                                    rowsQueue != null && !rowsQueue.isEmpty() ? rowsQueue.poll() : rows;
                            return preparedStatement(statementRows);
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return defaultValue(method);
                    });
        }

        private PreparedStatement preparedStatement(List<Map<String, Object>> statementRows) {
            List<Object> statementParameters = new java.util.ArrayList<>();
            parameterLog.add(statementParameters);
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setObject".equals(method.getName())) {
                            parameters.add(args[1]);
                            statementParameters.add(args[1]);
                            return null;
                        }
                        if ("setInt".equals(method.getName())) {
                            parameters.add(args[1]);
                            statementParameters.add(args[1]);
                            return null;
                        }
                        if ("setString".equals(method.getName())) {
                            parameters.add(args[1]);
                            statementParameters.add(args[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            return resultSet(statementRows);
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
                        case "getBoolean" -> rows.get(index).get((String) args[0]);
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
