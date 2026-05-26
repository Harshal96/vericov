package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.TestRunRepository;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.testresults.TestRun;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public class JdbcTestRunRepository implements TestRunRepository {
    private final DataSource dataSource;

    public JdbcTestRunRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void save(CoverageAnalysisInput input, List<TestRun> runs, Instant completedAt) {
        if (runs.isEmpty()) {
            return;
        }
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteExistingRuns(connection, input);
                insertRuns(connection, runs);
                insertRunsCompletedEvent(connection, input, runs, completedAt);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save test runs for upload " + input.uploadId(), exception);
        }
    }

    private static void deleteExistingRuns(java.sql.Connection connection, CoverageAnalysisInput input)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                delete from vericov.test_runs
                where upload_id = ?
                """)) {
            statement.setObject(1, input.uploadId());
            statement.executeUpdate();
        }
    }

    private static void insertRuns(java.sql.Connection connection, List<TestRun> runs) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.test_runs (
                    id,
                    tenant_id,
                    repository_id,
                    upload_id,
                    upload_artifact_id,
                    commit_sha,
                    branch,
                    pull_request_number,
                    suite_name,
                    suite_index,
                    status,
                    total_count,
                    passed_count,
                    failed_count,
                    error_count,
                    skipped_count,
                    duration_ms,
                    created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (TestRun run : runs) {
                int index = 1;
                statement.setObject(index++, run.id());
                statement.setObject(index++, run.tenantId());
                statement.setObject(index++, run.repositoryId());
                statement.setObject(index++, run.uploadId());
                statement.setObject(index++, run.uploadArtifactId());
                statement.setString(index++, run.commitSha());
                statement.setString(index++, run.branch());
                if (run.pullRequestNumber() == null) {
                    statement.setNull(index++, Types.INTEGER);
                } else {
                    statement.setInt(index++, run.pullRequestNumber());
                }
                statement.setString(index++, run.suiteName());
                statement.setInt(index++, run.suiteIndex());
                statement.setString(index++, run.status());
                statement.setInt(index++, run.totalCount());
                statement.setInt(index++, run.passedCount());
                statement.setInt(index++, run.failedCount());
                statement.setInt(index++, run.errorCount());
                statement.setInt(index++, run.skippedCount());
                statement.setLong(index++, run.durationMs());
                statement.setObject(index, utc(run.createdAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertRunsCompletedEvent(
            java.sql.Connection connection,
            CoverageAnalysisInput input,
            List<TestRun> runs,
            Instant completedAt) throws SQLException {
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
                    'test.runs.completed',
                    jsonb_build_object(
                        'test_run_count', ?,
                        'total_count', ?,
                        'passed_count', ?,
                        'failed_count', ?,
                        'error_count', ?,
                        'skipped_count', ?,
                        'duration_ms', ?
                    ),
                    ?
                )
                """)) {
            int index = 1;
            statement.setObject(index++, input.tenantId());
            statement.setObject(index++, input.uploadId());
            statement.setInt(index++, runs.size());
            statement.setInt(index++, sumInt(runs, TestRun::totalCount));
            statement.setInt(index++, sumInt(runs, TestRun::passedCount));
            statement.setInt(index++, sumInt(runs, TestRun::failedCount));
            statement.setInt(index++, sumInt(runs, TestRun::errorCount));
            statement.setInt(index++, sumInt(runs, TestRun::skippedCount));
            statement.setLong(index++, runs.stream().mapToLong(TestRun::durationMs).sum());
            statement.setObject(index, utc(completedAt));
            statement.executeUpdate();
        }
    }

    private static int sumInt(List<TestRun> runs, java.util.function.ToIntFunction<TestRun> mapper) {
        return runs.stream().mapToInt(mapper).sum();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void rollbackQuietly(java.sql.Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database failure.
        }
    }
}
