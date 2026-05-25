package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcCoverageAnalysisInputRepository implements CoverageAnalysisInputRepository {
    private final DataSource dataSource;

    public JdbcCoverageAnalysisInputRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public CoverageAnalysisInput load(UUID uploadId) {
        try (var connection = dataSource.getConnection()) {
            UploadRow upload = findUpload(connection, uploadId);
            List<CoverageInputArtifact> artifacts = findArtifacts(connection, uploadId);
            return new CoverageAnalysisInput(
                    upload.id(),
                    upload.tenantId(),
                    upload.repositoryId(),
                    upload.organizationId(),
                    upload.provider(),
                    upload.commitSha(),
                    upload.branch(),
                    upload.pullRequestNumber(),
                    artifacts);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load coverage analysis input for upload " + uploadId, exception);
        }
    }

    private static UploadRow findUpload(java.sql.Connection connection, UUID uploadId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select u.id, u.tenant_id, u.repository_id, r.org_id, r.provider,
                       u.commit_sha, u.branch, u.pull_request_number
                from vericov.uploads u
                join vericov.repositories r on r.id = u.repository_id
                where u.id = ?
                """)) {
            statement.setObject(1, uploadId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Upload not found: " + uploadId);
                }
                return new UploadRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("repository_id", UUID.class),
                        resultSet.getObject("org_id", UUID.class),
                        resultSet.getString("provider"),
                        resultSet.getString("commit_sha"),
                        resultSet.getString("branch"),
                        nullableInteger(resultSet, "pull_request_number"));
            }
        }
    }

    private static List<CoverageInputArtifact> findArtifacts(java.sql.Connection connection, UUID uploadId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select name, kind, format, storage_bucket, storage_path, sha256_hex
                from vericov.upload_artifacts
                where upload_id = ?
                order by name
                """)) {
            statement.setObject(1, uploadId);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageInputArtifact> artifacts = new ArrayList<>();
                while (resultSet.next()) {
                    artifacts.add(new CoverageInputArtifact(
                            resultSet.getString("name"),
                            resultSet.getString("kind"),
                            resultSet.getString("format"),
                            resultSet.getString("storage_bucket"),
                            resultSet.getString("storage_path"),
                            resultSet.getString("sha256_hex")));
                }
                return List.copyOf(artifacts);
            }
        }
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record UploadRow(
            UUID id,
            UUID tenantId,
            UUID repositoryId,
            UUID organizationId,
            String provider,
            String commitSha,
            String branch,
            Integer pullRequestNumber) {
    }
}
