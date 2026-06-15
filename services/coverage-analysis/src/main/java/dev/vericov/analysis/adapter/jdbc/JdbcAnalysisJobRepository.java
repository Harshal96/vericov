package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.AnalysisJobRepository;
import dev.vericov.analysis.domain.AnalysisFailureDecision;
import dev.vericov.analysis.domain.AnalysisJobStartResult;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcAnalysisJobRepository implements AnalysisJobRepository {
    private final DataSource dataSource;
    private final int leaseTimeoutSeconds;

    public JdbcAnalysisJobRepository(DataSource dataSource) {
        this(dataSource, 300);
    }

    public JdbcAnalysisJobRepository(DataSource dataSource, int leaseTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.leaseTimeoutSeconds = leaseTimeoutSeconds;
    }

    @Override
    public AnalysisJobStartResult startJob(UUID jobId, String workerId, Instant startedAt) {
        String sql = """
                update vericov.analysis_jobs
                set status = 'running',
                    attempts = attempts + 1,
                    locked_by = ?,
                    locked_at = ?,
                    lease_expires_at = ?,
                    started_at = coalesce(started_at, ?),
                    last_error = null
                where id = ?
                  and (status = 'queued' or (status = 'running' and lease_expires_at < now()))
                """;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (var statement = connection.prepareStatement(sql)) {
                    OffsetDateTime started = utc(startedAt);
                    statement.setString(1, workerId);
                    statement.setObject(2, started);
                    statement.setObject(3, utc(startedAt.plusSeconds(leaseTimeoutSeconds)));
                    statement.setObject(4, started);
                    statement.setObject(5, jobId);
                    updated = statement.executeUpdate();
                }
                if (updated == 1) {
                    markUploadProcessing(connection, jobId);
                }
                connection.commit();
                return updated == 1 ? AnalysisJobStartResult.started() : readUnclaimableJobStatus(jobId);
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to start analysis job " + jobId, exception);
        }
    }

    @Override
    public void completeJob(UUID jobId, Instant finishedAt) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertAttempt(connection, jobId, "succeeded", finishedAt, null);
                try (var statement = connection.prepareStatement("""
                        update vericov.analysis_jobs
                        set status = 'succeeded',
                            finished_at = ?,
                            locked_by = null,
                            locked_at = null,
                            lease_expires_at = null,
                            last_error = null
                        where id = ?
                        """)) {
                    statement.setObject(1, utc(finishedAt));
                    statement.setObject(2, jobId);
                    statement.executeUpdate();
                }
                markUploadProcessed(connection, jobId, finishedAt);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to complete analysis job " + jobId, exception);
        }
    }

    @Override
    public AnalysisFailureDecision recordFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertAttempt(connection, jobId, "failed", failedAt, errorMessage);
                boolean terminal;
                try (var statement = connection.prepareStatement("""
                        update vericov.analysis_jobs
                        set status = case when attempts >= max_attempts then 'failed' else 'queued' end,
                            finished_at = case when attempts >= max_attempts then ? else null end,
                            locked_by = null,
                            locked_at = null,
                            lease_expires_at = null,
                            last_error = ?
                        where id = ?
                        returning attempts >= max_attempts as terminal
                        """)) {
                    statement.setObject(1, utc(failedAt));
                    statement.setString(2, errorMessage);
                    statement.setObject(3, jobId);
                    try (var resultSet = statement.executeQuery()) {
                        terminal = resultSet.next() && resultSet.getBoolean("terminal");
                    }
                }
                if (terminal) {
                    markUploadFailed(connection, jobId, failedAt, errorMessage);
                } else {
                    markUploadQueued(connection, jobId, errorMessage);
                }
                connection.commit();
                return terminal ? AnalysisFailureDecision.deadLetter() : AnalysisFailureDecision.retryLater();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record analysis job failure " + jobId, exception);
        }
    }

    @Override
    public void recordTerminalFailure(UUID jobId, String workerId, Instant failedAt, String errorMessage) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertAttempt(connection, jobId, "failed", failedAt, errorMessage);
                try (var statement = connection.prepareStatement("""
                        update vericov.analysis_jobs
                        set status = 'failed',
                            finished_at = ?,
                            locked_by = null,
                            locked_at = null,
                            lease_expires_at = null,
                            last_error = ?
                        where id = ?
                        """)) {
                    statement.setObject(1, utc(failedAt));
                    statement.setString(2, errorMessage);
                    statement.setObject(3, jobId);
                    statement.executeUpdate();
                }
                markUploadFailed(connection, jobId, failedAt, errorMessage);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record terminal analysis failure " + jobId, exception);
        }
    }

    private AnalysisJobStartResult readUnclaimableJobStatus(UUID jobId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select status
                        from vericov.analysis_jobs
                        where id = ?
                        """)) {
            statement.setObject(1, jobId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return AnalysisJobStartResult.alreadyFinished();
                }
                String status = resultSet.getString("status");
                if ("succeeded".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                    return AnalysisJobStartResult.alreadyFinished();
                }
                return AnalysisJobStartResult.busy();
            }
        }
    }

    private static void markUploadProcessing(java.sql.Connection connection, UUID jobId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.uploads
                set status = 'processing',
                    error_code = null,
                    error_message = null
                where id = (
                    select upload_id
                    from vericov.analysis_jobs
                    where id = ?
                )
                """)) {
            statement.setObject(1, jobId);
            statement.executeUpdate();
        }
    }

    private static void markUploadProcessed(java.sql.Connection connection, UUID jobId, Instant finishedAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.uploads
                set status = 'processed',
                    completed_at = ?,
                    error_code = null,
                    error_message = null
                where id = (
                    select upload_id
                    from vericov.analysis_jobs
                    where id = ?
                )
                """)) {
            statement.setObject(1, utc(finishedAt));
            statement.setObject(2, jobId);
            statement.executeUpdate();
        }
    }

    private static void markUploadQueued(java.sql.Connection connection, UUID jobId, String errorMessage) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.uploads
                set status = 'queued',
                    error_code = 'analysis_retrying',
                    error_message = ?
                where id = (
                    select upload_id
                    from vericov.analysis_jobs
                    where id = ?
                )
                """)) {
            statement.setString(1, errorMessage);
            statement.setObject(2, jobId);
            statement.executeUpdate();
        }
    }

    private static void markUploadFailed(
            java.sql.Connection connection,
            UUID jobId,
            Instant failedAt,
            String errorMessage) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.uploads
                set status = 'failed',
                    completed_at = ?,
                    error_code = 'analysis_failed',
                    error_message = ?
                where id = (
                    select upload_id
                    from vericov.analysis_jobs
                    where id = ?
                )
                """)) {
            statement.setObject(1, utc(failedAt));
            statement.setString(2, errorMessage);
            statement.setObject(3, jobId);
            statement.executeUpdate();
        }
    }

    private static void insertAttempt(
            java.sql.Connection connection,
            UUID jobId,
            String status,
            Instant finishedAt,
            String errorMessage) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.analysis_job_attempts (
                    analysis_job_id,
                    worker_id,
                    attempt_number,
                    status,
                    started_at,
                    finished_at,
                    error_message
                )
                select id, coalesce(locked_by, 'unknown'), attempts, ?, coalesce(started_at, ?), ?, ?
                from vericov.analysis_jobs
                where id = ?
                """)) {
            statement.setString(1, status);
            statement.setObject(2, utc(finishedAt));
            statement.setObject(3, utc(finishedAt));
            statement.setString(4, errorMessage);
            statement.setObject(5, jobId);
            statement.executeUpdate();
        }
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
