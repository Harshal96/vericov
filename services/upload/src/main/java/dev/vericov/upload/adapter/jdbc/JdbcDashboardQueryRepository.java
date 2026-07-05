package dev.vericov.upload.adapter.jdbc;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.CoverageMetricDetails;
import dev.vericov.upload.application.DashboardComponentRollup;
import dev.vericov.upload.application.DashboardFileLineHit;
import dev.vericov.upload.application.DashboardFileSummary;
import dev.vericov.upload.application.DashboardGapCounts;
import dev.vericov.upload.application.DashboardGapFinding;
import dev.vericov.upload.application.DashboardGateConfig;
import dev.vericov.upload.application.DashboardGateEvaluation;
import dev.vericov.upload.application.DashboardPullRequestDiff;
import dev.vericov.upload.application.DashboardPullRequestDiffDetails;
import dev.vericov.upload.application.DashboardPullRequestDiffFile;
import dev.vericov.upload.application.DashboardReport;
import dev.vericov.upload.application.DashboardReportDetails;
import dev.vericov.upload.application.DashboardReportListItem;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
import dev.vericov.upload.application.DashboardTestRun;
import dev.vericov.upload.application.DashboardTrendPoint;
import dev.vericov.upload.application.DashboardUploadArtifact;
import dev.vericov.upload.application.DashboardUploadDetails;
import dev.vericov.upload.application.DashboardUploadEvent;
import dev.vericov.upload.application.DashboardUploadListItem;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcDashboardQueryRepository implements DashboardQueryRepository {
    private final DataSource dataSource;

    public JdbcDashboardQueryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public DashboardOverview overview(UUID tenantId) {
        try {
            return overviewFromSeparateStatements(tenantId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Dashboard overview lookup failed");
        }
    }

    @Override
    public List<DashboardRepositoryOverview> repositories(UUID tenantId) {
        try {
            return repositoryOverviews(tenantId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository overview lookup failed");
        }
    }

    @Override
    public Map<UUID, List<BigDecimal>> sparklines(UUID tenantId, int perRepository) {
        try {
            return sparklinePoints(tenantId, perRepository);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository sparkline lookup failed");
        }
    }

    @Override
    public Optional<DashboardRepository> repository(UUID tenantId, UUID repositoryId) {
        try {
            return repositoryById(tenantId, repositoryId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository lookup failed");
        }
    }

    @Override
    public List<DashboardTrendPoint> trend(UUID tenantId, UUID repositoryId, String branch, int limit) {
        try {
            return trendPoints(tenantId, repositoryId, branch, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository trend lookup failed");
        }
    }

    @Override
    public List<DashboardReportListItem> reports(UUID tenantId, UUID repositoryId, int limit) {
        try {
            return recentReports(tenantId, repositoryId, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository report history lookup failed");
        }
    }

    @Override
    public Optional<DashboardReportDetails> report(UUID tenantId, UUID reportId) {
        try {
            return reportById(tenantId, reportId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report lookup failed");
        }
    }

    @Override
    public List<DashboardFileSummary> reportFiles(UUID tenantId, UUID reportId) {
        try {
            return reportFileSummaries(tenantId, reportId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report file summary lookup failed");
        }
    }

    @Override
    public boolean reportFileExists(UUID tenantId, UUID reportId, String filePath) {
        try {
            return reportFileExistsByPath(tenantId, reportId, filePath);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report file lookup failed");
        }
    }

    @Override
    public List<DashboardFileLineHit> reportLineHits(UUID tenantId, UUID reportId, String filePath) {
        try {
            return reportFileLineHits(tenantId, reportId, filePath);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report line hit lookup failed");
        }
    }

    @Override
    public List<String> similarReportFilePaths(UUID tenantId, UUID reportId, String basename, int max) {
        try {
            return similarReportFilePathsByBasename(tenantId, reportId, basename, max);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Similar report file lookup failed");
        }
    }

    @Override
    public List<DashboardComponentRollup> reportComponents(UUID tenantId, UUID reportId) {
        try {
            return reportComponentRollups(tenantId, reportId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report component lookup failed");
        }
    }

    @Override
    public List<DashboardGapFinding> gaps(UUID tenantId, String status, int limit) {
        try {
            return latestDefaultBranchGaps(tenantId, status, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Dashboard gap lookup failed");
        }
    }

    @Override
    public List<DashboardGapFinding> repositoryGaps(UUID tenantId, UUID repositoryId, String status, int limit) {
        try {
            return latestDefaultBranchRepositoryGaps(tenantId, repositoryId, status, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository gap lookup failed");
        }
    }

    @Override
    public DashboardGapCounts repositoryGapCounts(UUID tenantId, UUID repositoryId) {
        try {
            return latestDefaultBranchRepositoryGapCounts(tenantId, repositoryId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository gap count lookup failed");
        }
    }

    @Override
    public List<DashboardGateConfig> gateConfigs(UUID tenantId, UUID repositoryId) {
        try {
            return repositoryGateConfigurations(tenantId, repositoryId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository gate configuration lookup failed");
        }
    }

    @Override
    public List<DashboardGateEvaluation> repositoryGateEvaluations(UUID tenantId, UUID repositoryId, int limit) {
        try {
            return repositoryGateEvaluationHistory(tenantId, repositoryId, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Repository gate evaluation lookup failed");
        }
    }

    @Override
    public List<DashboardGateEvaluation> reportGates(UUID tenantId, UUID reportId) {
        try {
            return reportGateEvaluations(tenantId, reportId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Report gate lookup failed");
        }
    }

    @Override
    public List<DashboardPullRequestDiff> pullRequestDiffs(UUID tenantId, UUID repositoryId, int limit) {
        try {
            return latestPullRequestDiffs(tenantId, repositoryId, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Pull request diff lookup failed");
        }
    }

    @Override
    public Optional<DashboardPullRequestDiffDetails> pullRequestDiff(UUID tenantId, UUID diffId) {
        try {
            return pullRequestDiffWithFiles(tenantId, diffId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Pull request diff lookup failed");
        }
    }

    @Override
    public List<DashboardTestRun> testRuns(UUID tenantId, UUID repositoryId, int limit) {
        try {
            return repositoryTestRuns(tenantId, repositoryId, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Test run lookup failed");
        }
    }

    @Override
    public List<DashboardUploadListItem> listUploads(UUID tenantId, UUID repositoryId, String status, int limit) {
        try {
            return tenantUploads(tenantId, repositoryId, status, limit);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Upload lookup failed");
        }
    }

    @Override
    public Optional<DashboardUploadDetails> uploadDetails(UUID tenantId, UUID uploadId) {
        try {
            return uploadWithEvents(tenantId, uploadId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Upload detail lookup failed");
        }
    }

    @Override
    public List<DashboardUploadArtifact> uploadArtifacts(UUID tenantId, UUID uploadId) {
        try {
            return tenantUploadArtifacts(tenantId, uploadId);
        } catch (SQLException exception) {
            throw new InvalidUploadException("dashboard_query_failed", "Upload artifact lookup failed");
        }
    }

    private List<DashboardUploadListItem> tenantUploads(UUID tenantId, UUID repositoryId, String status, int limit)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select uploads.id, uploads.repository_id, uploads.commit_sha, uploads.branch,
                               uploads.pull_request_number, uploads.ci_provider, uploads.ci_build_url,
                               uploads.status, uploads.accepted_at, uploads.completed_at,
                               uploads.error_code, uploads.error_message,
                               reports.id as coverage_report_id
                        from vericov.uploads uploads
                        left join vericov.coverage_reports reports
                          on reports.upload_id = uploads.id
                        where uploads.tenant_id = ?
                          and (?::uuid is null or uploads.repository_id = ?)
                          and (?::text is null or uploads.status = ?)
                        order by uploads.accepted_at desc
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setObject(3, repositoryId);
            statement.setObject(4, status);
            statement.setObject(5, status);
            statement.setInt(6, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardUploadListItem> uploads = new ArrayList<>();
                while (resultSet.next()) {
                    uploads.add(dashboardUploadListItem(resultSet));
                }
                return List.copyOf(uploads);
            }
        }
    }

    private Optional<DashboardUploadDetails> uploadWithEvents(UUID tenantId, UUID uploadId) throws SQLException {
        DashboardUploadListItem upload;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select uploads.id, uploads.repository_id, uploads.commit_sha, uploads.branch,
                               uploads.pull_request_number, uploads.ci_provider, uploads.ci_build_url,
                               uploads.status, uploads.accepted_at, uploads.completed_at,
                               uploads.error_code, uploads.error_message,
                               reports.id as coverage_report_id
                        from vericov.uploads uploads
                        left join vericov.coverage_reports reports
                          on reports.upload_id = uploads.id
                        where uploads.tenant_id = ?
                          and uploads.id = ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, uploadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                upload = dashboardUploadListItem(resultSet);
            }
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select event_type, payload::text as payload, created_at
                        from vericov.upload_events
                        where tenant_id = ?
                          and upload_id = ?
                        order by created_at asc
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, uploadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardUploadEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(new DashboardUploadEvent(
                            resultSet.getString("event_type"),
                            resultSet.getString("payload"),
                            instant(resultSet, "created_at")));
                }
                return Optional.of(new DashboardUploadDetails(upload, events));
            }
        }
    }

    private List<DashboardUploadArtifact> tenantUploadArtifacts(UUID tenantId, UUID uploadId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select artifacts.name, artifacts.kind, artifacts.format, artifacts.size_bytes,
                               artifacts.created_at
                        from vericov.upload_artifacts artifacts
                        join vericov.uploads uploads
                          on uploads.id = artifacts.upload_id
                        where uploads.tenant_id = ?
                          and artifacts.upload_id = ?
                        order by artifacts.created_at asc
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, uploadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardUploadArtifact> artifacts = new ArrayList<>();
                while (resultSet.next()) {
                    artifacts.add(new DashboardUploadArtifact(
                            resultSet.getString("name"),
                            resultSet.getString("kind"),
                            resultSet.getString("format"),
                            resultSet.getLong("size_bytes"),
                            instant(resultSet, "created_at")));
                }
                return List.copyOf(artifacts);
            }
        }
    }

    private static DashboardUploadListItem dashboardUploadListItem(ResultSet resultSet) throws SQLException {
        return new DashboardUploadListItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                nullableInteger(resultSet, "pull_request_number"),
                resultSet.getString("ci_provider"),
                resultSet.getString("ci_build_url"),
                resultSet.getString("status"),
                instant(resultSet, "accepted_at"),
                nullableInstant(resultSet, "completed_at"),
                nullableUuid(resultSet, "coverage_report_id"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"));
    }

    private List<DashboardTestRun> repositoryTestRuns(UUID tenantId, UUID repositoryId, int limit)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, suite_name, status, total_count, passed_count, failed_count,
                               error_count, skipped_count, duration_ms, commit_sha, branch, created_at
                        from vericov.test_runs
                        where tenant_id = ?
                          and repository_id = ?
                        order by created_at desc, suite_index
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardTestRun> runs = new ArrayList<>();
                while (resultSet.next()) {
                    runs.add(dashboardTestRun(resultSet));
                }
                return List.copyOf(runs);
            }
        }
    }

    private DashboardOverview overviewFromSeparateStatements(UUID tenantId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        with latest_default_reports as (
                            select distinct on (repositories.id)
                                repositories.id as repository_id,
                                reports.line_covered,
                                reports.line_total
                            from vericov.repositories repositories
                            join vericov.coverage_reports reports
                              on reports.repository_id = repositories.id
                             and reports.tenant_id = repositories.tenant_id
                             and reports.branch = repositories.default_branch
                             and reports.status = 'complete'
                            where repositories.tenant_id = ?
                              and repositories.status = 'active'
                            order by repositories.id, reports.created_at desc
                        )
                        select
                            (select count(*) from vericov.repositories where tenant_id = ?) as repo_count,
                            (select count(*) from vericov.repositories where tenant_id = ? and status = 'active') as active_repo_count,
                            case
                                when coalesce(sum(line_total), 0) = 0 then null
                                else round((sum(line_covered)::numeric / sum(line_total)::numeric) * 100, 2)
                            end as weighted_line_coverage,
                            (select count(*) from vericov.coverage_reports where tenant_id = ?) as total_reports,
                            (select count(*) from vericov.coverage_gap_findings where tenant_id = ? and status = 'active') as active_gaps,
                            (select count(*) from vericov.coverage_gap_findings where tenant_id = ? and status = 'active' and risk_level = 'critical') as critical_gaps,
                            (
                                select count(*)
                                from vericov.gate_evaluations
                                where tenant_id = ?
                                  and status = 'failed'
                                  and evaluated_at >= now() - interval '30 days'
                            ) as failing_gates
                        from latest_default_reports
                        """)) {
            for (int index = 1; index <= 7; index++) {
                statement.setObject(index, tenantId);
            }
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new DashboardOverview(0, 0, null, 0, 0, 0, 0);
                }
                return new DashboardOverview(
                        resultSet.getLong("repo_count"),
                        resultSet.getLong("active_repo_count"),
                        resultSet.getBigDecimal("weighted_line_coverage"),
                        resultSet.getLong("total_reports"),
                        resultSet.getLong("active_gaps"),
                        resultSet.getLong("critical_gaps"),
                        resultSet.getLong("failing_gates"));
            }
        }
    }

    private List<DashboardRepositoryOverview> repositoryOverviews(UUID tenantId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            r.id, r.full_name, r.provider, r.default_branch, r.visibility, r.status, r.updated_at,
                            cr.id as report_id, cr.commit_sha, cr.created_at as report_created_at,
                            cr.line_covered, cr.line_total, cr.branch_covered, cr.branch_total,
                            cr.function_covered, cr.function_total, cr.statement_covered, cr.statement_total,
                            case
                                when cr.id is null or prev.line_total is null or prev.line_total = 0
                                     or cr.line_total is null or cr.line_total = 0 then null
                                else round(
                                    ((cr.line_covered::numeric / cr.line_total::numeric) * 100)
                                    - ((prev.line_covered::numeric / prev.line_total::numeric) * 100),
                                    2
                                )
                            end as line_delta,
                            (
                                select count(*)
                                from vericov.coverage_reports c2
                                where c2.tenant_id = r.tenant_id
                                  and c2.repository_id = r.id
                            ) as report_count,
                            (
                                select count(*)
                                from vericov.coverage_gap_findings gaps
                                where gaps.tenant_id = r.tenant_id
                                  and gaps.repository_id = r.id
                                  and gaps.status = 'active'
                            ) as active_gaps,
                            (
                                select count(*)
                                from vericov.gate_evaluations gates
                                where gates.tenant_id = r.tenant_id
                                  and gates.coverage_report_id = cr.id
                                  and gates.status = 'failed'
                            ) as failing_gates
                        from vericov.repositories r
                        left join lateral (
                            select *
                            from vericov.coverage_reports cr
                            where cr.tenant_id = r.tenant_id
                              and cr.repository_id = r.id
                              and cr.branch = r.default_branch
                              and cr.status = 'complete'
                            order by cr.created_at desc
                            limit 1
                        ) cr on true
                        left join lateral (
                            select p.line_covered, p.line_total
                            from vericov.coverage_reports p
                            where p.tenant_id = r.tenant_id
                              and p.repository_id = r.id
                              and p.branch = r.default_branch
                              and p.status = 'complete'
                              and p.created_at < cr.created_at
                            order by p.created_at desc
                            limit 1
                        ) prev on true
                        where r.tenant_id = ?
                        order by cr.created_at desc nulls last, r.full_name
                        """)) {
            statement.setObject(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardRepositoryOverview> repositories = new ArrayList<>();
                while (resultSet.next()) {
                    repositories.add(new DashboardRepositoryOverview(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("full_name"),
                            resultSet.getString("provider"),
                            resultSet.getString("default_branch"),
                            resultSet.getString("visibility"),
                            resultSet.getString("status"),
                            instant(resultSet, "updated_at"),
                            nullableUuid(resultSet, "report_id"),
                            resultSet.getString("commit_sha"),
                            nullableInstant(resultSet, "report_created_at"),
                            nullableInteger(resultSet, "line_covered"),
                            nullableInteger(resultSet, "line_total"),
                            nullableInteger(resultSet, "branch_covered"),
                            nullableInteger(resultSet, "branch_total"),
                            nullableInteger(resultSet, "function_covered"),
                            nullableInteger(resultSet, "function_total"),
                            nullableInteger(resultSet, "statement_covered"),
                            nullableInteger(resultSet, "statement_total"),
                            resultSet.getBigDecimal("line_delta"),
                            resultSet.getLong("report_count"),
                            resultSet.getLong("active_gaps"),
                            resultSet.getLong("failing_gates")));
                }
                return List.copyOf(repositories);
            }
        }
    }

    private Map<UUID, List<BigDecimal>> sparklinePoints(UUID tenantId, int perRepository) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select repository_id, line_pct
                        from (
                            select
                                cr.repository_id,
                                case
                                    when cr.line_total = 0 then null
                                    else round((cr.line_covered::numeric / cr.line_total::numeric) * 100, 2)
                                end as line_pct,
                                cr.created_at,
                                row_number() over (
                                    partition by cr.repository_id
                                    order by cr.created_at desc
                                ) as rn
                            from vericov.coverage_reports cr
                            join vericov.repositories r
                              on r.tenant_id = cr.tenant_id
                             and r.id = cr.repository_id
                            where cr.tenant_id = ?
                              and cr.status = 'complete'
                              and cr.branch = r.default_branch
                        ) points
                        where rn <= ?
                          and line_pct is not null
                        order by repository_id, created_at asc
                        """)) {
            statement.setObject(1, tenantId);
            statement.setInt(2, perRepository);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, List<BigDecimal>> mutable = new LinkedHashMap<>();
                while (resultSet.next()) {
                    UUID repositoryId = resultSet.getObject("repository_id", UUID.class);
                    mutable.computeIfAbsent(repositoryId, ignored -> new ArrayList<>())
                            .add(resultSet.getBigDecimal("line_pct"));
                }
                Map<UUID, List<BigDecimal>> immutable = new LinkedHashMap<>();
                mutable.forEach((repositoryId, values) -> immutable.put(repositoryId, List.copyOf(values)));
                return Map.copyOf(immutable);
            }
        }
    }

    private Optional<DashboardRepository> repositoryById(UUID tenantId, UUID repositoryId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, full_name, provider, default_branch, visibility, status, updated_at
                        from vericov.repositories
                        where tenant_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DashboardRepository(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("full_name"),
                        resultSet.getString("provider"),
                        resultSet.getString("default_branch"),
                        resultSet.getString("visibility"),
                        resultSet.getString("status"),
                        instant(resultSet, "updated_at")));
            }
        }
    }

    private List<DashboardTrendPoint> trendPoints(
            UUID tenantId, UUID repositoryId, String branch, int limit) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select *
                        from (
                            select
                                cr.id as report_id,
                                cr.commit_sha,
                                cr.created_at,
                                case
                                    when cr.line_total = 0 then null
                                    else round((cr.line_covered::numeric / cr.line_total::numeric) * 100, 2)
                                end as line_pct,
                                case
                                    when cr.branch_total = 0 then null
                                    else round((cr.branch_covered::numeric / cr.branch_total::numeric) * 100, 2)
                                end as branch_pct,
                                case
                                    when cr.function_total = 0 then null
                                    else round((cr.function_covered::numeric / cr.function_total::numeric) * 100, 2)
                                end as function_pct,
                                case
                                    when cr.statement_total = 0 then null
                                    else round((cr.statement_covered::numeric / cr.statement_total::numeric) * 100, 2)
                                end as statement_pct
                            from vericov.coverage_reports cr
                            join vericov.repositories r
                              on r.tenant_id = cr.tenant_id
                             and r.id = cr.repository_id
                            where cr.tenant_id = ?
                              and cr.repository_id = ?
                              and cr.status = 'complete'
                              and cr.branch = coalesce(nullif(?, ''), r.default_branch)
                            order by cr.created_at desc
                            limit ?
                        ) trend
                        order by created_at asc
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setString(3, branch);
            statement.setInt(4, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardTrendPoint> points = new ArrayList<>();
                while (resultSet.next()) {
                    points.add(new DashboardTrendPoint(
                            resultSet.getObject("report_id", UUID.class),
                            resultSet.getString("commit_sha"),
                            instant(resultSet, "created_at"),
                            resultSet.getBigDecimal("line_pct"),
                            resultSet.getBigDecimal("branch_pct"),
                            resultSet.getBigDecimal("function_pct"),
                            resultSet.getBigDecimal("statement_pct")));
                }
                return List.copyOf(points);
            }
        }
    }

    private List<DashboardReportListItem> recentReports(UUID tenantId, UUID repositoryId, int limit)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        with scoped_reports as (
                            select cr.*
                            from vericov.coverage_reports cr
                            where cr.tenant_id = ?
                              and cr.repository_id = ?
                        ),
                        complete_deltas as (
                            select
                                id,
                                case
                                    when line_total = 0 or prev_line_total is null or prev_line_total = 0 then null
                                    else round(
                                        ((line_covered::numeric / line_total::numeric) * 100)
                                        - ((prev_line_covered::numeric / prev_line_total::numeric) * 100),
                                        2
                                    )
                                end as line_delta
                            from (
                                select
                                    scoped_reports.*,
                                    lag(line_covered) over same_branch as prev_line_covered,
                                    lag(line_total) over same_branch as prev_line_total
                                from scoped_reports
                                where status = 'complete'
                                window same_branch as (partition by branch order by created_at)
                            ) complete_reports
                        )
                        select
                            scoped_reports.*,
                            complete_deltas.line_delta,
                            uploads.ci_provider,
                            uploads.ci_build_url,
                            uploads.flags
                        from scoped_reports
                        join vericov.uploads uploads
                          on uploads.tenant_id = scoped_reports.tenant_id
                         and uploads.id = scoped_reports.upload_id
                        left join complete_deltas
                          on complete_deltas.id = scoped_reports.id
                        order by scoped_reports.created_at desc
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardReportListItem> reports = new ArrayList<>();
                while (resultSet.next()) {
                    reports.add(new DashboardReportListItem(
                            dashboardReport(resultSet),
                            resultSet.getBigDecimal("line_delta"),
                            resultSet.getString("ci_provider"),
                            resultSet.getString("ci_build_url"),
                            textArray(resultSet, "flags")));
                }
                return List.copyOf(reports);
            }
        }
    }

    private Optional<DashboardReportDetails> reportById(UUID tenantId, UUID reportId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            cr.*,
                            r.full_name,
                            r.provider,
                            r.default_branch,
                            r.visibility,
                            r.status as repo_status,
                            r.updated_at as repo_updated_at
                        from vericov.coverage_reports cr
                        join vericov.repositories r
                          on r.tenant_id = cr.tenant_id
                         and r.id = cr.repository_id
                        where cr.tenant_id = ?
                          and cr.id = ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                DashboardRepository repository = new DashboardRepository(
                        resultSet.getObject("repository_id", UUID.class),
                        resultSet.getString("full_name"),
                        resultSet.getString("provider"),
                        resultSet.getString("default_branch"),
                        resultSet.getString("visibility"),
                        resultSet.getString("repo_status"),
                        instant(resultSet, "repo_updated_at"));
                return Optional.of(new DashboardReportDetails(dashboardReport(resultSet), repository));
            }
        }
    }

    private List<DashboardFileSummary> reportFileSummaries(UUID tenantId, UUID reportId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select file_path, package_name, owners,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total
                        from vericov.coverage_file_summaries
                        where tenant_id = ?
                          and coverage_report_id = ?
                        order by
                            (case when line_total = 0 then 100.0 else line_covered * 100.0 / line_total end) asc,
                            file_path asc
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardFileSummary> files = new ArrayList<>();
                while (resultSet.next()) {
                    files.add(new DashboardFileSummary(
                            resultSet.getString("file_path"),
                            resultSet.getString("package_name"),
                            textArray(resultSet, "owners"),
                            metric(resultSet, "line"),
                            metric(resultSet, "branch"),
                            metric(resultSet, "function"),
                            metric(resultSet, "statement")));
                }
                return List.copyOf(files);
            }
        }
    }

    private boolean reportFileExistsByPath(UUID tenantId, UUID reportId, String filePath) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select 1
                        from vericov.coverage_file_summaries
                        where tenant_id = ?
                          and coverage_report_id = ?
                          and file_path = ?
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            statement.setString(3, filePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<DashboardFileLineHit> reportFileLineHits(UUID tenantId, UUID reportId, String filePath)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select line_number, hits
                        from vericov.coverage_line_hits
                        where tenant_id = ?
                          and coverage_report_id = ?
                          and file_path = ?
                        order by line_number
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            statement.setString(3, filePath);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardFileLineHit> lines = new ArrayList<>();
                while (resultSet.next()) {
                    lines.add(new DashboardFileLineHit(
                            resultSet.getInt("line_number"),
                            resultSet.getLong("hits")));
                }
                return List.copyOf(lines);
            }
        }
    }

    private List<String> similarReportFilePathsByBasename(
            UUID tenantId, UUID reportId, String basename, int max) throws SQLException {
        if (basename == null || basename.isBlank()) {
            return List.of();
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select file_path
                        from vericov.coverage_file_summaries
                        where tenant_id = ?
                          and coverage_report_id = ?
                          and (file_path = ? or file_path like ?)
                        order by file_path
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            statement.setString(3, basename);
            statement.setString(4, "%/" + basename.replace("%", "\\%").replace("_", "\\_"));
            statement.setInt(5, max);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> paths = new ArrayList<>();
                while (resultSet.next()) {
                    paths.add(resultSet.getString("file_path"));
                }
                return List.copyOf(paths);
            }
        }
    }

    private List<DashboardComponentRollup> reportComponentRollups(UUID tenantId, UUID reportId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select component_key, owners,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               gap_count, debt_count, risk_score_total, highest_active_risk_level
                        from vericov.component_coverage_rollups
                        where tenant_id = ?
                          and coverage_report_id = ?
                        order by risk_score_total desc, coalesce(owners[1], ''), component_key
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardComponentRollup> components = new ArrayList<>();
                while (resultSet.next()) {
                    List<String> owners = textArray(resultSet, "owners");
                    components.add(new DashboardComponentRollup(
                            resultSet.getString("component_key"),
                            owners.isEmpty() ? "" : owners.getFirst(),
                            metric(resultSet, "line"),
                            metric(resultSet, "branch"),
                            metric(resultSet, "function"),
                            metric(resultSet, "statement"),
                            resultSet.getLong("gap_count"),
                            resultSet.getLong("debt_count"),
                            resultSet.getBigDecimal("risk_score_total"),
                            resultSet.getString("highest_active_risk_level")));
                }
                return List.copyOf(components);
            }
        }
    }

    private List<DashboardGapFinding> latestDefaultBranchGaps(UUID tenantId, String status, int limit)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        with latest_default_reports as (
                            select distinct on (repositories.id)
                                repositories.id as repository_id,
                                reports.id as report_id
                            from vericov.repositories repositories
                            join vericov.coverage_reports reports
                              on reports.repository_id = repositories.id
                             and reports.tenant_id = repositories.tenant_id
                             and reports.branch = repositories.default_branch
                             and reports.status = 'complete'
                            where repositories.tenant_id = ?
                              and repositories.status = 'active'
                            order by repositories.id, reports.created_at desc
                        )
                        select gaps.id, gaps.coverage_report_id, gaps.repository_id,
                               gaps.file_path, gaps.target_type, gaps.line_start, gaps.line_end,
                               gaps.symbol_name, gaps.reason_code, gaps.explanation, gaps.confidence,
                               gaps.risk_score, gaps.risk_level, gaps.owners, gaps.next_action,
                               gaps.status, gaps.commit_sha, gaps.pull_request_number
                        from vericov.coverage_gap_findings gaps
                        join latest_default_reports latest
                          on latest.repository_id = gaps.repository_id
                         and latest.report_id = gaps.coverage_report_id
                        where gaps.tenant_id = ?
                          and (?::text is null or gaps.status = ?)
                        order by gaps.risk_score desc, gaps.id
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, tenantId);
            statement.setObject(3, status);
            statement.setObject(4, status);
            statement.setInt(5, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                return dashboardGapFindings(resultSet);
            }
        }
    }

    private List<DashboardGapFinding> latestDefaultBranchRepositoryGaps(
            UUID tenantId, UUID repositoryId, String status, int limit) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        with latest_default_report as (
                            select reports.id as report_id
                            from vericov.repositories repositories
                            join vericov.coverage_reports reports
                              on reports.repository_id = repositories.id
                             and reports.tenant_id = repositories.tenant_id
                             and reports.branch = repositories.default_branch
                             and reports.status = 'complete'
                            where repositories.tenant_id = ?
                              and repositories.id = ?
                              and repositories.status = 'active'
                            order by reports.created_at desc
                            limit 1
                        )
                        select gaps.id, gaps.coverage_report_id, gaps.repository_id,
                               gaps.file_path, gaps.target_type, gaps.line_start, gaps.line_end,
                               gaps.symbol_name, gaps.reason_code, gaps.explanation, gaps.confidence,
                               gaps.risk_score, gaps.risk_level, gaps.owners, gaps.next_action,
                               gaps.status, gaps.commit_sha, gaps.pull_request_number
                        from vericov.coverage_gap_findings gaps
                        join latest_default_report latest
                          on latest.report_id = gaps.coverage_report_id
                        where gaps.tenant_id = ?
                          and gaps.repository_id = ?
                          and (?::text is null or gaps.status = ?)
                        order by gaps.risk_score desc, gaps.id
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setObject(3, tenantId);
            statement.setObject(4, repositoryId);
            statement.setObject(5, status);
            statement.setObject(6, status);
            statement.setInt(7, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                return dashboardGapFindings(resultSet);
            }
        }
    }

    private DashboardGapCounts latestDefaultBranchRepositoryGapCounts(UUID tenantId, UUID repositoryId)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        with latest_default_report as (
                            select reports.id as report_id
                            from vericov.repositories repositories
                            join vericov.coverage_reports reports
                              on reports.repository_id = repositories.id
                             and reports.tenant_id = repositories.tenant_id
                             and reports.branch = repositories.default_branch
                             and reports.status = 'complete'
                            where repositories.tenant_id = ?
                              and repositories.id = ?
                              and repositories.status = 'active'
                            order by reports.created_at desc
                            limit 1
                        )
                        select gaps.risk_level, count(*) as n
                        from vericov.coverage_gap_findings gaps
                        join latest_default_report latest
                          on latest.report_id = gaps.coverage_report_id
                        where gaps.tenant_id = ?
                          and gaps.repository_id = ?
                          and gaps.status = 'active'
                        group by gaps.risk_level
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setObject(3, tenantId);
            statement.setObject(4, repositoryId);
            long critical = 0;
            long high = 0;
            long medium = 0;
            long low = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long count = resultSet.getLong("n");
                    switch (resultSet.getString("risk_level")) {
                        case "critical" -> critical = count;
                        case "high" -> high = count;
                        case "medium" -> medium = count;
                        case "low" -> low = count;
                        default -> { }
                    }
                }
            }
            return new DashboardGapCounts(critical, high, medium, low);
        }
    }

    private List<DashboardGateConfig> repositoryGateConfigurations(UUID tenantId, UUID repositoryId)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, name, gate_type, metric, threshold, max_drop, blocking, status
                        from vericov.repository_gate_configurations
                        where tenant_id = ?
                          and repository_id = ?
                        order by blocking desc, name
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardGateConfig> configs = new ArrayList<>();
                while (resultSet.next()) {
                    configs.add(new DashboardGateConfig(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("name"),
                            resultSet.getString("gate_type"),
                            resultSet.getString("metric"),
                            resultSet.getBigDecimal("threshold"),
                            resultSet.getBigDecimal("max_drop"),
                            resultSet.getBoolean("blocking"),
                            resultSet.getString("status")));
                }
                return List.copyOf(configs);
            }
        }
    }

    private List<DashboardGateEvaluation> repositoryGateEvaluationHistory(
            UUID tenantId, UUID repositoryId, int limit) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, gate_name, gate_type, metric, threshold, actual, status, blocking,
                               commit_sha, branch, pull_request_number, evaluated_at, coverage_report_id
                        from vericov.gate_evaluations
                        where tenant_id = ?
                          and repository_id = ?
                        order by evaluated_at desc
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                return dashboardGateEvaluations(resultSet);
            }
        }
    }

    private List<DashboardGateEvaluation> reportGateEvaluations(UUID tenantId, UUID reportId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, gate_name, gate_type, metric, threshold, actual, status, blocking,
                               commit_sha, branch, pull_request_number, evaluated_at, coverage_report_id
                        from vericov.gate_evaluations
                        where tenant_id = ?
                          and coverage_report_id = ?
                        order by blocking desc, gate_name
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return dashboardGateEvaluations(resultSet);
            }
        }
    }

    private List<DashboardPullRequestDiff> latestPullRequestDiffs(UUID tenantId, UUID repositoryId, int limit)
            throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select *
                        from (
                            select distinct on (d.pull_request_number)
                                d.id, d.pull_request_number, d.base_sha, d.head_sha, d.status,
                                d.patch_line_covered, d.patch_line_total,
                                d.newly_missed_line_count, d.lost_coverage_line_count,
                                d.created_at, d.coverage_report_id,
                                case
                                    when cr.line_total = 0 then null
                                    else round((cr.line_covered::numeric / cr.line_total::numeric) * 100, 2)
                                end as project_line_pct
                            from vericov.pull_request_coverage_diffs d
                            join vericov.coverage_reports cr on cr.id = d.coverage_report_id
                            where d.tenant_id = ?
                              and d.repository_id = ?
                            order by d.pull_request_number, d.created_at desc
                        ) latest
                        order by created_at desc
                        limit ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DashboardPullRequestDiff> diffs = new ArrayList<>();
                while (resultSet.next()) {
                    diffs.add(dashboardPullRequestDiff(resultSet));
                }
                return List.copyOf(diffs);
            }
        }
    }

    private Optional<DashboardPullRequestDiffDetails> pullRequestDiffWithFiles(UUID tenantId, UUID diffId)
            throws SQLException {
        try (var connection = dataSource.getConnection()) {
            DashboardPullRequestDiff diff;
            try (var statement = connection.prepareStatement("""
                    select d.id, d.pull_request_number, d.base_sha, d.head_sha, d.status,
                           d.patch_line_covered, d.patch_line_total,
                           d.newly_missed_line_count, d.lost_coverage_line_count,
                           d.created_at, d.coverage_report_id,
                           case
                               when cr.line_total = 0 then null
                               else round((cr.line_covered::numeric / cr.line_total::numeric) * 100, 2)
                           end as project_line_pct
                    from vericov.pull_request_coverage_diffs d
                    join vericov.coverage_reports cr on cr.id = d.coverage_report_id
                    where d.tenant_id = ?
                      and d.id = ?
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, diffId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    diff = dashboardPullRequestDiff(resultSet);
                }
            }
            List<DashboardPullRequestDiffFile> files = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    select file_path, change_status, patch_line_covered, patch_line_total,
                           newly_missed_line_count, lost_coverage_line_count
                    from vericov.pull_request_coverage_diff_files
                    where tenant_id = ?
                      and pr_diff_id = ?
                    order by newly_missed_line_count desc, file_path
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, diffId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        files.add(new DashboardPullRequestDiffFile(
                                resultSet.getString("file_path"),
                                resultSet.getString("change_status"),
                                metric(resultSet, "patch_line"),
                                resultSet.getInt("newly_missed_line_count"),
                                resultSet.getInt("lost_coverage_line_count")));
                    }
                }
            }
            return Optional.of(new DashboardPullRequestDiffDetails(diff, files));
        }
    }

    private static DashboardPullRequestDiff dashboardPullRequestDiff(ResultSet resultSet) throws SQLException {
        return new DashboardPullRequestDiff(
                resultSet.getObject("id", UUID.class),
                resultSet.getInt("pull_request_number"),
                resultSet.getString("base_sha"),
                resultSet.getString("head_sha"),
                resultSet.getString("status"),
                metric(resultSet, "patch_line"),
                resultSet.getInt("newly_missed_line_count"),
                resultSet.getInt("lost_coverage_line_count"),
                instant(resultSet, "created_at"),
                resultSet.getObject("coverage_report_id", UUID.class),
                resultSet.getBigDecimal("project_line_pct"));
    }

    private static DashboardTestRun dashboardTestRun(ResultSet resultSet) throws SQLException {
        return new DashboardTestRun(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("suite_name"),
                resultSet.getString("status"),
                resultSet.getInt("total_count"),
                resultSet.getInt("passed_count"),
                resultSet.getInt("failed_count"),
                resultSet.getInt("error_count"),
                resultSet.getInt("skipped_count"),
                resultSet.getLong("duration_ms"),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                instant(resultSet, "created_at"));
    }

    private static List<DashboardGapFinding> dashboardGapFindings(ResultSet resultSet) throws SQLException {
        List<DashboardGapFinding> gaps = new ArrayList<>();
        while (resultSet.next()) {
            gaps.add(new DashboardGapFinding(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("coverage_report_id", UUID.class),
                    resultSet.getObject("repository_id", UUID.class),
                    resultSet.getString("file_path"),
                    resultSet.getString("target_type"),
                    nullableInteger(resultSet, "line_start"),
                    nullableInteger(resultSet, "line_end"),
                    resultSet.getString("symbol_name"),
                    resultSet.getString("reason_code"),
                    resultSet.getString("explanation"),
                    resultSet.getString("confidence"),
                    resultSet.getBigDecimal("risk_score"),
                    resultSet.getString("risk_level"),
                    textArray(resultSet, "owners"),
                    resultSet.getString("next_action"),
                    resultSet.getString("status"),
                    resultSet.getString("commit_sha"),
                    nullableInteger(resultSet, "pull_request_number")));
        }
        return List.copyOf(gaps);
    }

    private static List<DashboardGateEvaluation> dashboardGateEvaluations(ResultSet resultSet) throws SQLException {
        List<DashboardGateEvaluation> evaluations = new ArrayList<>();
        while (resultSet.next()) {
            evaluations.add(new DashboardGateEvaluation(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("gate_name"),
                    resultSet.getString("gate_type"),
                    resultSet.getString("metric"),
                    resultSet.getBigDecimal("threshold"),
                    resultSet.getBigDecimal("actual"),
                    resultSet.getString("status"),
                    resultSet.getBoolean("blocking"),
                    resultSet.getString("commit_sha"),
                    resultSet.getString("branch"),
                    nullableInteger(resultSet, "pull_request_number"),
                    instant(resultSet, "evaluated_at"),
                    nullableUuid(resultSet, "coverage_report_id")));
        }
        return List.copyOf(evaluations);
    }

    private static DashboardReport dashboardReport(ResultSet resultSet) throws SQLException {
        return new DashboardReport(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("upload_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                nullableInteger(resultSet, "pull_request_number"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                resultSet.getInt("line_covered"),
                resultSet.getInt("line_total"),
                resultSet.getInt("branch_covered"),
                resultSet.getInt("branch_total"),
                resultSet.getInt("function_covered"),
                resultSet.getInt("function_total"),
                resultSet.getInt("statement_covered"),
                resultSet.getInt("statement_total"));
    }

    private static UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static CoverageMetricDetails metric(ResultSet resultSet, String prefix) throws SQLException {
        return new CoverageMetricDetails(
                resultSet.getLong(prefix + "_covered"),
                resultSet.getLong(prefix + "_total"));
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static java.time.Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static List<String> textArray(ResultSet resultSet, String column) throws SQLException {
        java.sql.Array array = resultSet.getArray(column);
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }

}
