package dev.vericov.analysis.adapter.jdbc;

import dev.vericov.componentconfig.ComponentConfigException;
import dev.vericov.componentconfig.ComponentConfigJson;
import dev.vericov.componentconfig.ComponentConfigSnapshot;
import dev.vericov.analysis.application.port.CoverageAnalysisInputRepository;
import dev.vericov.analysis.coverage.CoverageAnalysisInput;
import dev.vericov.analysis.coverage.CoverageInputArtifact;
import dev.vericov.analysis.domain.NonRetryableAnalysisException;
import dev.vericov.ignore.CoverageIgnoreRules;
import dev.vericov.ignore.InvalidCoverageIgnoreRuleException;
import jakarta.json.Json;
import jakarta.json.JsonString;
import java.io.StringReader;
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
                    upload.provider(),
                    upload.commitSha(),
                    upload.branch(),
                    upload.pullRequestNumber(),
                    upload.baseSha(),
                    upload.ignore(),
                    upload.configSnapshotJson(),
                    upload.configSha256(),
                    artifacts);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load coverage analysis input for upload " + uploadId, exception);
        }
    }

    private static UploadRow findUpload(java.sql.Connection connection, UUID uploadId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select u.id, u.tenant_id, u.repository_id, r.provider,
                       u.commit_sha, u.branch, u.pull_request_number, u.base_sha, u.ignore_rules,
                       u.config_snapshot_json::text as config_snapshot_json, u.config_sha256
                from vericov.uploads u
                join vericov.repositories r on r.id = u.repository_id
                where u.id = ?
                """)) {
            statement.setObject(1, uploadId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Upload not found: " + uploadId);
                }
                SnapshotData snapshot = snapshot(
                        resultSet.getString("config_snapshot_json"),
                        resultSet.getString("config_sha256"),
                        resultSet.getString("ignore_rules"));
                return new UploadRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("repository_id", UUID.class),
                        resultSet.getString("provider"),
                        resultSet.getString("commit_sha"),
                        resultSet.getString("branch"),
                        nullableInteger(resultSet, "pull_request_number"),
                        resultSet.getString("base_sha"),
                        snapshot.ignore(),
                        snapshot.json(),
                        snapshot.sha256());
            }
        }
    }

    private static List<CoverageInputArtifact> findArtifacts(java.sql.Connection connection, UUID uploadId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select id, name, kind, format, storage_bucket, storage_path, sha256_hex
                from vericov.upload_artifacts
                where upload_id = ?
                order by name
                """)) {
            statement.setObject(1, uploadId);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageInputArtifact> artifacts = new ArrayList<>();
                while (resultSet.next()) {
                    artifacts.add(new CoverageInputArtifact(
                            resultSet.getObject("id", UUID.class),
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

    private static List<String> parseAndValidateIgnoreRules(String json) {
        List<String> rules;
        try (var reader = Json.createReader(new StringReader(
                json == null || json.isBlank() ? "[]" : json))) {
            rules = reader.readArray().getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .toList();
        } catch (RuntimeException exception) {
            throw new NonRetryableAnalysisException(
                    "Persisted upload ignore rules must be a JSON string array",
                    exception);
        }
        try {
            return CoverageIgnoreRules.validate(rules);
        } catch (InvalidCoverageIgnoreRuleException exception) {
            throw new NonRetryableAnalysisException(
                    "Invalid persisted upload ignore rules: " + exception.getMessage(),
                    exception);
        }
    }

    private static SnapshotData snapshot(
            String snapshotJson,
            String configSha256,
            String legacyIgnoreJson) {
        if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
                ComponentConfigSnapshot snapshot = ComponentConfigJson.parse(snapshotJson);
                String computed = ComponentConfigJson.sha256(snapshot);
                if (configSha256 == null || !computed.equals(configSha256)) {
                    throw new NonRetryableAnalysisException(
                            "Persisted config snapshot hash does not match config_sha256");
                }
                return new SnapshotData(
                        snapshot.ignore(),
                        ComponentConfigJson.canonicalJson(snapshot),
                        computed);
            } catch (ComponentConfigException exception) {
                throw new NonRetryableAnalysisException(
                        "Invalid persisted config snapshot: " + exception.getMessage(),
                        exception);
            }
        }
        List<String> ignore = parseAndValidateIgnoreRules(legacyIgnoreJson);
        if (ignore.isEmpty()) {
            return new SnapshotData(List.of(), null, null);
        }
        ComponentConfigSnapshot snapshot = new ComponentConfigSnapshot(1, ignore, List.of());
        return new SnapshotData(
                ignore,
                ComponentConfigJson.canonicalJson(snapshot),
                ComponentConfigJson.sha256(snapshot));
    }

    private record UploadRow(
            UUID id,
            UUID tenantId,
            UUID repositoryId,
            String provider,
            String commitSha,
            String branch,
            Integer pullRequestNumber,
            String baseSha,
            List<String> ignore,
            String configSnapshotJson,
            String configSha256) {
    }

    private record SnapshotData(
            List<String> ignore,
            String json,
            String sha256) {
    }
}
