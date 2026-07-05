package dev.vericov.upload.adapter.jdbc;

import dev.vericov.upload.application.DashboardOverview;
import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.DashboardQueryRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;
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
                                  and created_at >= now() - interval '30 days'
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
}
