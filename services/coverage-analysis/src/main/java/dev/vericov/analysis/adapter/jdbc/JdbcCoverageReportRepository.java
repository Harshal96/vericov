package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.CoverageReportRepository;
import dev.vericov.analysis.coverage.CoverageComponentRollup;
import dev.vericov.analysis.coverage.CoverageFileSummary;
import dev.vericov.analysis.coverage.CoverageLineHit;
import dev.vericov.analysis.coverage.CoverageMetric;
import dev.vericov.analysis.coverage.CoverageReport;
import dev.vericov.analysis.coverage.CoverageReportSummary;
import dev.vericov.analysis.gaps.CoverageGapFinding;
import dev.vericov.analysis.gates.GateEvaluation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcCoverageReportRepository implements CoverageReportRepository {
    private final DataSource dataSource;
    private final AnalysisJsonCodec codec = new AnalysisJsonCodec();

    public JdbcCoverageReportRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void save(CoverageReport report) {
        save(report, List.of());
    }

    @Override
    public void save(CoverageReport report, List<GateEvaluation> evaluations) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteExistingGateEvaluations(connection, report);
                deleteExistingReport(connection, report);
                insertCoverageReport(connection, report);
                insertFileSummaries(connection, report);
                insertComponentRollups(connection, report);
                insertGapFindings(connection, report);
                insertLineHits(connection, report);
                insertGateEvaluations(connection, evaluations);
                markUploadProcessed(connection, report);
                insertReportCompletedEvent(connection, report);
                insertGapsExtractedEvent(connection, report);
                insertRiskScoredEvent(connection, report);
                insertGatesEvaluatedEvent(connection, report, evaluations);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save coverage report for upload " + report.uploadId(), exception);
        }
    }

    @Override
    public Optional<CoverageReportSummary> findLatestByCommit(UUID repositoryId, String commitSha) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, upload_id, commit_sha, branch,
                               pull_request_number, created_at, updated_at
                        from vericov.coverage_reports
                        where repository_id = ?
                          and commit_sha = ?
                          and status = 'complete'
                        order by created_at desc, id desc
                        limit 1
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, commitSha);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readSummary(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find coverage report for commit " + commitSha, exception);
        }
    }

    @Override
    public List<CoverageLineHit> findLineHits(UUID coverageReportId) {
        return findLineHits(coverageReportId, null);
    }

    @Override
    public List<CoverageLineHit> findLineHits(UUID coverageReportId, String filePath) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select file_path, line_number, hits
                        from vericov.coverage_line_hits
                        where coverage_report_id = ?
                          and (? is null or file_path = ?)
                        order by file_path, line_number
                        """)) {
            statement.setObject(1, coverageReportId);
            statement.setString(2, filePath);
            statement.setString(3, filePath);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageLineHit> lineHits = new ArrayList<>();
                while (resultSet.next()) {
                    lineHits.add(new CoverageLineHit(
                            resultSet.getString("file_path"),
                            resultSet.getInt("line_number"),
                            resultSet.getLong("hits")));
                }
                return List.copyOf(lineHits);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find line hits for report " + coverageReportId, exception);
        }
    }

    private static void deleteExistingReport(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                delete from vericov.coverage_reports
                where upload_id = ?
                """)) {
            statement.setObject(1, report.uploadId());
            statement.executeUpdate();
        }
    }

    private static void deleteExistingGateEvaluations(java.sql.Connection connection, CoverageReport report)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                delete from vericov.gate_evaluations
                where coverage_report_id in (
                    select id
                    from vericov.coverage_reports
                    where upload_id = ?
                )
                """)) {
            statement.setObject(1, report.uploadId());
            statement.executeUpdate();
        }
    }

    private static void insertCoverageReport(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.coverage_reports (
                    id,
                    tenant_id,
                    repository_id,
                    upload_id,
                    commit_sha,
                    branch,
                    pull_request_number,
                    status,
                    line_covered,
                    line_total,
                    branch_covered,
                    branch_total,
                    function_covered,
                    function_total,
                    statement_covered,
                    statement_total,
                    normalized_storage_bucket,
                    normalized_storage_path,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'complete', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setObject(index++, report.reportId());
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.repositoryId());
            statement.setObject(index++, report.uploadId());
            statement.setString(index++, report.commitSha());
            statement.setString(index++, report.branchName());
            if (report.pullRequestNumber() == null) {
                statement.setNull(index++, Types.INTEGER);
            } else {
                statement.setInt(index++, report.pullRequestNumber());
            }
            setMetric(statement, index, report.line());
            index += 2;
            setMetric(statement, index, report.branch());
            index += 2;
            setMetric(statement, index, report.function());
            index += 2;
            setMetric(statement, index, report.statement());
            index += 2;
            statement.setString(index++, report.normalizedStorageBucket());
            statement.setString(index++, report.normalizedStoragePath());
            statement.setObject(index++, utc(report.generatedAt()));
            statement.setObject(index, utc(report.generatedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertFileSummaries(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.coverage_file_summaries (
                    id,
                    tenant_id,
                    coverage_report_id,
                    repository_id,
                    commit_sha,
                    file_path,
                    component_id,
                    package_name,
                    owners,
                    line_covered,
                    line_total,
                    branch_covered,
                    branch_total,
                    function_covered,
                    function_total,
                    statement_covered,
                    statement_total
                )
                values (extensions.gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (CoverageFileSummary file : report.files()) {
                int index = 1;
                statement.setObject(index++, report.tenantId());
                statement.setObject(index++, report.reportId());
                statement.setObject(index++, report.repositoryId());
                statement.setString(index++, report.commitSha());
                statement.setString(index++, file.filePath());
                statement.setObject(index++, file.componentId());
                statement.setString(index++, file.packageName());
                statement.setArray(index++, connection.createArrayOf("text", file.owners().toArray(String[]::new)));
                setMetric(statement, index, file.line());
                index += 2;
                setMetric(statement, index, file.branch());
                index += 2;
                setMetric(statement, index, file.function());
                index += 2;
                setMetric(statement, index, file.statement());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertComponentRollups(java.sql.Connection connection, CoverageReport report) throws SQLException {
        if (report.componentRollups().isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement("""
                insert into vericov.component_coverage_rollups (
                    id,
                    tenant_id,
                    repository_id,
                    coverage_report_id,
                    component_id,
                    owner,
                    line_covered,
                    line_total,
                    branch_covered,
                    branch_total,
                    function_covered,
                    function_total,
                    statement_covered,
                    statement_total,
                    gap_count,
                    debt_count,
                    risk_score_total,
                    highest_active_risk_level,
                    created_at
                )
                values (
                    extensions.gen_random_uuid(),
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                """)) {
            for (CoverageComponentRollup rollup : report.componentRollups()) {
                int index = 1;
                statement.setObject(index++, report.tenantId());
                statement.setObject(index++, report.repositoryId());
                statement.setObject(index++, report.reportId());
                statement.setObject(index++, rollup.componentId());
                statement.setString(index++, rollup.owner());
                setMetric(statement, index, rollup.line());
                index += 2;
                setMetric(statement, index, rollup.branch());
                index += 2;
                setMetric(statement, index, rollup.function());
                index += 2;
                setMetric(statement, index, rollup.statement());
                index += 2;
                statement.setInt(index++, rollup.gapCount());
                statement.setInt(index++, rollup.debtCount());
                statement.setBigDecimal(index++, rollup.riskScoreTotal());
                setNullableString(statement, index++, rollup.highestActiveRiskLevel());
                statement.setObject(index, utc(report.generatedAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertGapFindings(java.sql.Connection connection, CoverageReport report) throws SQLException {
        if (report.gapFindings().isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement("""
                insert into vericov.coverage_gap_findings (
                    id,
                    tenant_id,
                    repository_id,
                    coverage_report_id,
                    pr_diff_id,
                    component_id,
                    commit_sha,
                    pull_request_number,
                    file_path,
                    target_type,
                    line_start,
                    line_end,
                    symbol_name,
                    reason_code,
                    explanation,
                    confidence,
                    risk_score,
                    risk_level,
                    owners,
                    next_action,
                    status,
                    evidence_json,
                    created_at,
                    updated_at
                )
                values (
                    ?,
                    ?,
                    ?,
                    ?,
                    (select id from vericov.pull_request_coverage_diffs where coverage_report_id = ?),
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
                    ?,
                    ?
                )
                """)) {
            for (CoverageGapFinding finding : report.gapFindings()) {
                int index = 1;
                statement.setObject(index++, finding.id());
                statement.setObject(index++, finding.tenantId());
                statement.setObject(index++, finding.repositoryId());
                statement.setObject(index++, finding.coverageReportId());
                statement.setObject(index++, finding.coverageReportId());
                statement.setObject(index++, finding.componentId());
                statement.setString(index++, finding.commitSha());
                if (finding.pullRequestNumber() == null) {
                    statement.setNull(index++, Types.INTEGER);
                } else {
                    statement.setInt(index++, finding.pullRequestNumber());
                }
                statement.setString(index++, finding.filePath());
                statement.setString(index++, finding.targetType());
                setNullableInteger(statement, index++, finding.lineStart());
                setNullableInteger(statement, index++, finding.lineEnd());
                setNullableString(statement, index++, finding.symbolName());
                statement.setString(index++, finding.reasonCode());
                statement.setString(index++, finding.explanation());
                statement.setString(index++, finding.confidence());
                statement.setBigDecimal(index++, finding.riskScore());
                statement.setString(index++, finding.riskLevel());
                statement.setArray(index++, connection.createArrayOf("text", finding.owners().toArray(String[]::new)));
                statement.setString(index++, finding.nextAction());
                statement.setString(index++, finding.status());
                statement.setString(index++, codec.toJsonObject(finding.evidence()));
                statement.setObject(index++, utc(finding.createdAt()));
                statement.setObject(index, utc(finding.updatedAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertLineHits(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.coverage_line_hits (
                    id,
                    tenant_id,
                    coverage_report_id,
                    repository_id,
                    commit_sha,
                    file_path,
                    line_number,
                    hits,
                    created_at
                )
                values (extensions.gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (CoverageLineHit lineHit : report.lineHits()) {
                int index = 1;
                statement.setObject(index++, report.tenantId());
                statement.setObject(index++, report.reportId());
                statement.setObject(index++, report.repositoryId());
                statement.setString(index++, report.commitSha());
                statement.setString(index++, lineHit.filePath());
                statement.setInt(index++, lineHit.lineNumber());
                statement.setLong(index++, lineHit.hits());
                statement.setObject(index++, utc(report.generatedAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertGateEvaluations(
            java.sql.Connection connection,
            List<GateEvaluation> evaluations) throws SQLException {
        if (evaluations.isEmpty()) {
            return;
        }
        try (var statement = connection.prepareStatement("""
                insert into vericov.gate_evaluations (
                    id,
                    tenant_id,
                    repository_id,
                    coverage_report_id,
                    commit_sha,
                    branch,
                    pull_request_number,
                    gate_name,
                    gate_type,
                    metric,
                    threshold,
                    actual,
                    status,
                    blocking,
                    details_json,
                    evaluated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """)) {
            for (GateEvaluation evaluation : evaluations) {
                int index = 1;
                statement.setObject(index++, evaluation.id());
                statement.setObject(index++, evaluation.tenantId());
                statement.setObject(index++, evaluation.repositoryId());
                statement.setObject(index++, evaluation.coverageReportId());
                statement.setString(index++, evaluation.commitSha());
                statement.setString(index++, evaluation.branch());
                if (evaluation.pullRequestNumber() == null) {
                    statement.setNull(index++, Types.INTEGER);
                } else {
                    statement.setInt(index++, evaluation.pullRequestNumber());
                }
                statement.setString(index++, evaluation.gateName());
                statement.setString(index++, evaluation.gateType());
                statement.setString(index++, evaluation.metric());
                statement.setBigDecimal(index++, evaluation.threshold());
                statement.setBigDecimal(index++, evaluation.actual());
                statement.setString(index++, evaluation.status());
                statement.setBoolean(index++, evaluation.blocking());
                statement.setString(index++, codec.toJsonObject(evaluation.details()));
                statement.setObject(index++, utc(evaluation.evaluatedAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void markUploadProcessed(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.uploads
                set status = 'processed',
                    completed_at = ?,
                    error_code = null,
                    error_message = null
                where id = ?
                """)) {
            statement.setObject(1, utc(report.generatedAt()));
            statement.setObject(2, report.uploadId());
            statement.executeUpdate();
        }
    }

    private static void insertReportCompletedEvent(java.sql.Connection connection, CoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_events (
                    tenant_id,
                    upload_id,
                    event_type,
                    payload,
                    created_at
                )
                values (
                    ?,
                    ?,
                    'coverage.report.completed',
                    jsonb_build_object(
                        'coverage_report_id', ?,
                        'line_covered', ?,
                        'line_total', ?,
                        'branch_covered', ?,
                        'branch_total', ?
                    ),
                    ?
                )
                """)) {
            int index = 1;
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.uploadId());
            statement.setString(index++, report.reportId().toString());
            statement.setInt(index++, report.line().covered());
            statement.setInt(index++, report.line().total());
            statement.setInt(index++, report.branch().covered());
            statement.setInt(index++, report.branch().total());
            statement.setObject(index, utc(report.generatedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertGapsExtractedEvent(java.sql.Connection connection, CoverageReport report) throws SQLException {
        if (report.gapFindings().isEmpty()) {
            return;
        }
        long debtSuppressed = report.gapFindings().stream()
                .filter(finding -> "debt_suppressed".equals(finding.status()))
                .count();
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_events (
                    tenant_id,
                    upload_id,
                    event_type,
                    payload,
                    created_at
                )
                values (
                    ?,
                    ?,
                    'coverage.gaps.extracted',
                    jsonb_build_object(
                        'coverage_report_id', ?,
                        'finding_count', ?,
                        'debt_suppressed_count', ?
                    ),
                    ?
                )
                """)) {
            int index = 1;
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.uploadId());
            statement.setString(index++, report.reportId().toString());
            statement.setInt(index++, report.gapFindings().size());
            statement.setLong(index++, debtSuppressed);
            statement.setObject(index, utc(report.generatedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertRiskScoredEvent(java.sql.Connection connection, CoverageReport report) throws SQLException {
        if (report.gapFindings().isEmpty()) {
            return;
        }
        long highOrCritical = report.gapFindings().stream()
                .filter(finding -> "high".equals(finding.riskLevel()) || "critical".equals(finding.riskLevel()))
                .count();
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_events (
                    tenant_id,
                    upload_id,
                    event_type,
                    payload,
                    created_at
                )
                values (
                    ?,
                    ?,
                    'coverage.risk.scored',
                    jsonb_build_object(
                        'coverage_report_id', ?,
                        'finding_count', ?,
                        'high_or_critical_count', ?
                    ),
                    ?
                )
                """)) {
            int index = 1;
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.uploadId());
            statement.setString(index++, report.reportId().toString());
            statement.setInt(index++, report.gapFindings().size());
            statement.setLong(index++, highOrCritical);
            statement.setObject(index, utc(report.generatedAt()));
            statement.executeUpdate();
        }
    }

    private static void insertGatesEvaluatedEvent(
            java.sql.Connection connection,
            CoverageReport report,
            List<GateEvaluation> evaluations) throws SQLException {
        if (evaluations.isEmpty()) {
            return;
        }
        long failed = evaluations.stream().filter(evaluation -> "failed".equals(evaluation.status())).count();
        long warnings = evaluations.stream().filter(evaluation -> "warning".equals(evaluation.status())).count();
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_events (
                    tenant_id,
                    upload_id,
                    event_type,
                    payload,
                    created_at
                )
                values (
                    ?,
                    ?,
                    'coverage.gates.evaluated',
                    jsonb_build_object(
                        'coverage_report_id', ?,
                        'gate_evaluation_count', ?,
                        'failed_gate_count', ?,
                        'warning_gate_count', ?
                    ),
                    ?
                )
                """)) {
            int index = 1;
            statement.setObject(index++, report.tenantId());
            statement.setObject(index++, report.uploadId());
            statement.setString(index++, report.reportId().toString());
            statement.setInt(index++, evaluations.size());
            statement.setLong(index++, failed);
            statement.setLong(index++, warnings);
            statement.setObject(index, utc(report.generatedAt()));
            statement.executeUpdate();
        }
    }

    private static void setMetric(java.sql.PreparedStatement statement, int startIndex, CoverageMetric metric)
            throws SQLException {
        statement.setInt(startIndex, metric.covered());
        statement.setInt(startIndex + 1, metric.total());
    }

    private static void setNullableInteger(java.sql.PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableString(java.sql.PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static CoverageReportSummary readSummary(ResultSet resultSet) throws SQLException {
        return new CoverageReportSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("upload_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                nullableInteger(resultSet, "pull_request_number"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, OffsetDateTime.class).toInstant();
    }

    private static void rollbackQuietly(java.sql.Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database failure.
        }
    }
}
