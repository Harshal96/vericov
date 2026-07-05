package dev.vericov.upload.adapter.jdbc;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.DashboardRepository;
import dev.vericov.upload.application.DashboardRepositoryOverview;
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

    private static UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static java.time.Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
