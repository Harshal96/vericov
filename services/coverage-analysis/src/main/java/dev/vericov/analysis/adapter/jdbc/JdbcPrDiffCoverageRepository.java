package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.PrDiffCoverageRepository;
import dev.vericov.analysis.diff.DiffCoverageFile;
import dev.vericov.analysis.diff.DiffCoverageLine;
import dev.vericov.analysis.diff.DiffCoverageReport;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcPrDiffCoverageRepository implements PrDiffCoverageRepository {
    private final DataSource dataSource;

    public JdbcPrDiffCoverageRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void save(
            UUID tenantId,
            UUID repositoryId,
            UUID coverageReportId,
            int pullRequestNumber,
            String providerKey,
            String status,
            DiffCoverageReport report) {
        UUID diffId = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteExisting(connection, coverageReportId);
                insertParent(
                        connection,
                        diffId,
                        tenantId,
                        repositoryId,
                        coverageReportId,
                        pullRequestNumber,
                        providerKey,
                        status,
                        report);
                insertFiles(connection, diffId, tenantId, repositoryId, report);
                insertLines(connection, diffId, tenantId, repositoryId, report);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save pull request diff coverage", exception);
        }
    }

    private static void deleteExisting(java.sql.Connection connection, UUID coverageReportId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                delete from vericov.pull_request_coverage_diffs
                where coverage_report_id = ?
                """)) {
            statement.setObject(1, coverageReportId);
            statement.executeUpdate();
        }
    }

    private static void insertParent(
            java.sql.Connection connection,
            UUID diffId,
            UUID tenantId,
            UUID repositoryId,
            UUID coverageReportId,
            int pullRequestNumber,
            String providerKey,
            String status,
            DiffCoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.pull_request_coverage_diffs (
                    id, tenant_id, repository_id, coverage_report_id, pull_request_number,
                    provider_key, base_sha, head_sha, status, patch_line_covered,
                    patch_line_total, newly_missed_line_count, lost_coverage_line_count,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """)) {
            int index = 1;
            statement.setObject(index++, diffId);
            statement.setObject(index++, tenantId);
            statement.setObject(index++, repositoryId);
            statement.setObject(index++, coverageReportId);
            statement.setInt(index++, pullRequestNumber);
            statement.setString(index++, providerKey);
            statement.setString(index++, report.baseSha());
            statement.setString(index++, report.headSha());
            statement.setString(index++, status);
            statement.setInt(index++, report.patchLineCovered());
            statement.setInt(index++, report.patchLineTotal());
            statement.setInt(index++, report.newlyMissedLineCount());
            statement.setInt(index, report.lostCoverageLineCount());
            statement.executeUpdate();
        }
    }

    private static void insertFiles(
            java.sql.Connection connection,
            UUID diffId,
            UUID tenantId,
            UUID repositoryId,
            DiffCoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.pull_request_coverage_diff_files (
                    id, tenant_id, pr_diff_id, repository_id, file_path, old_file_path,
                    change_status, patch_line_covered, patch_line_total,
                    newly_missed_line_count, lost_coverage_line_count
                )
                values (extensions.gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (DiffCoverageFile file : report.files()) {
                int index = 1;
                statement.setObject(index++, tenantId);
                statement.setObject(index++, diffId);
                statement.setObject(index++, repositoryId);
                statement.setString(index++, file.filePath());
                setNullableString(statement, index++, file.oldFilePath());
                statement.setString(index++, file.changeStatus());
                statement.setInt(index++, file.patchLineCovered());
                statement.setInt(index++, file.patchLineTotal());
                statement.setInt(index++, file.newlyMissedLineCount());
                statement.setInt(index, file.lostCoverageLineCount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertLines(
            java.sql.Connection connection,
            UUID diffId,
            UUID tenantId,
            UUID repositoryId,
            DiffCoverageReport report) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.pull_request_coverage_diff_lines (
                    id, tenant_id, pr_diff_id, repository_id, file_path, old_file_path,
                    base_line_number, head_line_number, change_type, executable,
                    base_hits, head_hits, newly_missed, lost_coverage
                )
                values (extensions.gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (DiffCoverageFile file : report.files()) {
                for (DiffCoverageLine line : file.lines()) {
                    int index = 1;
                    statement.setObject(index++, tenantId);
                    statement.setObject(index++, diffId);
                    statement.setObject(index++, repositoryId);
                    statement.setString(index++, line.filePath());
                    setNullableString(statement, index++, line.oldFilePath());
                    setNullableInteger(statement, index++, line.baseLineNumber());
                    setNullableInteger(statement, index++, line.headLineNumber());
                    statement.setString(index++, line.changeType().name().toLowerCase(java.util.Locale.ROOT));
                    statement.setBoolean(index++, line.executable());
                    setNullableLong(statement, index++, line.baseHits());
                    setNullableLong(statement, index++, line.headHits());
                    statement.setBoolean(index++, line.newlyMissed());
                    statement.setBoolean(index, line.lostCoverage());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
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

    private static void setNullableInteger(java.sql.PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setNullableLong(java.sql.PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void rollbackQuietly(java.sql.Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database failure.
        }
    }
}
