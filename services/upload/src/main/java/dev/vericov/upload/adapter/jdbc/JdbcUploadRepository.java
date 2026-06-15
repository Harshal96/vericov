package dev.vericov.upload.adapter.jdbc;

import dev.vericov.componentconfig.ComponentConfigJson;
import dev.vericov.componentconfig.ComponentConfigSnapshot;
import dev.vericov.upload.application.ComponentCoverageDetails;
import dev.vericov.upload.application.CoverageGateDetails;
import dev.vericov.upload.application.QueuedUpload;
import dev.vericov.upload.application.StoredArtifact;
import dev.vericov.upload.application.DuplicateUploadException;
import dev.vericov.upload.application.CoverageMetricDetails;
import dev.vericov.upload.application.CoverageReportDetails;
import dev.vericov.upload.application.CoverageWarningDetails;
import dev.vericov.upload.application.port.UploadRepository;
import dev.vericov.upload.domain.ArtifactKind;
import dev.vericov.upload.domain.UploadStatus;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcUploadRepository implements UploadRepository {
    private final DataSource dataSource;

    public JdbcUploadRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<QueuedUpload> findById(UUID uploadId) {
        return find("""
                where u.id = ?
                """, uploadId);
    }

    @Override
    public Optional<QueuedUpload> findByIdempotencyKey(UUID repositoryId, String idempotencyKey) {
        return find("""
                where u.repository_id = ? and u.idempotency_key = ?
                """, repositoryId, idempotencyKey);
    }

    @Override
    public void save(QueuedUpload upload, List<StoredArtifact> artifacts) {
        Objects.requireNonNull(upload, "upload");
        List<StoredArtifact> immutableArtifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        UUID analysisJobId = upload.analysisJobId()
                .orElseThrow(() -> new IllegalArgumentException("analysisJobId is required"));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertUpload(connection, upload);
                insertArtifacts(connection, upload.uploadId(), immutableArtifacts);
                insertAnalysisJob(connection, upload, analysisJobId);
                insertUploadEvent(connection, upload, analysisJobId);
                enqueueAnalysisJob(connection, analysisJobId);
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            if (isUniqueViolation(exception)) {
                throw new DuplicateUploadException(exception);
            }
            throw new IllegalStateException("Failed to save upload " + upload.uploadId(), exception);
        }
    }

    @Override
    public List<StoredArtifact> artifactsFor(UUID uploadId) {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select name, kind, format, content_type, size_bytes,
                               storage_bucket, storage_path, sha256_hex
                        from vericov.upload_artifacts
                        where upload_id = ?
                        order by name
                        """)) {
            statement.setObject(1, uploadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredArtifact> artifacts = new ArrayList<>();
                while (resultSet.next()) {
                    artifacts.add(new StoredArtifact(
                            resultSet.getString("name"),
                            ArtifactKind.fromWireValue(resultSet.getString("kind")),
                            resultSet.getString("format"),
                            resultSet.getString("content_type"),
                            resultSet.getLong("size_bytes"),
                            resultSet.getString("storage_bucket"),
                            resultSet.getString("storage_path"),
                            resultSet.getString("sha256_hex")));
                }
                return List.copyOf(artifacts);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load artifacts for upload " + uploadId, exception);
        }
    }

    @Override
    public Optional<CoverageReportDetails> coverageReportFor(UUID uploadId) {
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("""
                        select id, upload_id, repository_id, commit_sha, branch, pull_request_number,
                               status, line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               normalized_storage_bucket, normalized_storage_path,
                               config_sha256, gate_status, warnings_json::text as warnings_json,
                               created_at
                        from vericov.coverage_reports
                        where upload_id = ?
                        """)) {
                statement.setObject(1, uploadId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    UUID reportId = resultSet.getObject("id", UUID.class);
                    ComponentProjection projection = componentProjection(connection, reportId);
                    List<CoverageWarningDetails> warnings = parseWarnings(resultSet.getString("warnings_json"));
                    return Optional.of(new CoverageReportDetails(
                            resultSet.getObject("upload_id", UUID.class),
                            resultSet.getObject("repository_id", UUID.class),
                            resultSet.getString("commit_sha"),
                            resultSet.getString("branch"),
                            nullableInteger(resultSet, "pull_request_number"),
                            resultSet.getString("status"),
                            metric(resultSet, "line"),
                            metric(resultSet, "branch"),
                            metric(resultSet, "function"),
                            metric(resultSet, "statement"),
                            resultSet.getString("normalized_storage_bucket"),
                            resultSet.getString("normalized_storage_path"),
                            resultSet.getString("config_sha256"),
                            resultSet.getString("gate_status"),
                            warnings.isEmpty() ? projection.warnings() : warnings,
                            projection.components(),
                            resultSet.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load coverage report for upload " + uploadId, exception);
        }
    }

    private static ComponentProjection componentProjection(Connection connection, UUID reportId) throws SQLException {
        Map<String, List<CoverageGateDetails>> gatesByComponent = componentGates(connection, reportId);
        List<ComponentRow> rows = componentRows(connection, reportId);
        Map<String, List<ComponentRow>> childrenByParent = new HashMap<>();
        List<CoverageWarningDetails> warnings = new ArrayList<>();
        for (ComponentRow row : rows) {
            childrenByParent.computeIfAbsent(row.parentKey(), ignored -> new ArrayList<>()).add(row);
            if ("unassigned".equals(row.key()) && row.descendantFileCount() > 0) {
                warnings.add(new CoverageWarningDetails("unassigned_files", row.descendantFileCount()));
            }
        }
        Comparator<ComponentRow> order = Comparator
                .comparingInt(ComponentRow::position)
                .thenComparing(ComponentRow::key);
        childrenByParent.values().forEach(children -> children.sort(order));
        List<ComponentCoverageDetails> roots = childrenByParent.getOrDefault(null, List.of()).stream()
                .map(row -> componentDetails(row, childrenByParent, gatesByComponent))
                .toList();
        return new ComponentProjection(roots, warnings);
    }

    private static List<ComponentRow> componentRows(Connection connection, UUID reportId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select component_key, parent_component_key, component_path, depth, position,
                       name, owners, effective_gates_json::text as effective_gates_json,
                       line_covered, line_total, branch_covered, branch_total,
                       function_covered, function_total, statement_covered, statement_total,
                       direct_file_count, descendant_file_count, gap_count, debt_count,
                       risk_score_total, highest_active_risk_level
                from vericov.component_coverage_rollups
                where coverage_report_id = ?
                order by component_path, position, component_key
                """)) {
            statement.setObject(1, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ComponentRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new ComponentRow(
                            resultSet.getString("component_key"),
                            resultSet.getString("parent_component_key"),
                            textArray(resultSet, "component_path"),
                            resultSet.getInt("depth"),
                            resultSet.getInt("position"),
                            resultSet.getString("name"),
                            textArray(resultSet, "owners"),
                            parseGates(resultSet.getString("effective_gates_json")),
                            metric(resultSet, "line"),
                            metric(resultSet, "branch"),
                            metric(resultSet, "function"),
                            metric(resultSet, "statement"),
                            resultSet.getInt("direct_file_count"),
                            resultSet.getInt("descendant_file_count"),
                            resultSet.getInt("gap_count"),
                            resultSet.getInt("debt_count"),
                            resultSet.getBigDecimal("risk_score_total"),
                            resultSet.getString("highest_active_risk_level")));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static Map<String, List<CoverageGateDetails>> componentGates(
            Connection connection,
            UUID reportId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select scope_key, metric, threshold, actual, status, blocking
                from vericov.gate_evaluations
                where coverage_report_id = ?
                  and source = 'component_config'
                  and scope_type = 'component'
                order by scope_path, metric
                """)) {
            statement.setObject(1, reportId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, List<CoverageGateDetails>> gates = new LinkedHashMap<>();
                while (resultSet.next()) {
                    gates.computeIfAbsent(resultSet.getString("scope_key"), ignored -> new ArrayList<>())
                            .add(new CoverageGateDetails(
                                    resultSet.getString("metric"),
                                    resultSet.getBigDecimal("threshold"),
                                    resultSet.getBigDecimal("actual"),
                                    resultSet.getString("status"),
                                    resultSet.getBoolean("blocking")));
                }
                gates.replaceAll((ignored, values) -> List.copyOf(values));
                return Map.copyOf(gates);
            }
        }
    }

    private static ComponentCoverageDetails componentDetails(
            ComponentRow row,
            Map<String, List<ComponentRow>> childrenByParent,
            Map<String, List<CoverageGateDetails>> gatesByComponent) {
        List<ComponentCoverageDetails> children = childrenByParent.getOrDefault(row.key(), List.of()).stream()
                .map(child -> componentDetails(child, childrenByParent, gatesByComponent))
                .toList();
        return new ComponentCoverageDetails(
                row.key(),
                row.name(),
                row.path(),
                row.depth(),
                row.position(),
                row.owners(),
                row.effectiveGates(),
                row.line(),
                row.branchCoverage(),
                row.function(),
                row.statement(),
                row.directFileCount(),
                row.descendantFileCount(),
                row.gapCount(),
                row.debtCount(),
                row.riskScoreTotal(),
                row.highestActiveRiskLevel(),
                gatesByComponent.getOrDefault(row.key(), List.of()),
                children);
    }

    private static Map<String, BigDecimal> parseGates(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            Map<String, BigDecimal> gates = new LinkedHashMap<>();
            reader.readObject().forEach((metric, value) ->
                    gates.put(metric, ((JsonNumber) value).bigDecimalValue()));
            return Map.copyOf(gates);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored effective component gates must be a JSON object", exception);
        }
    }

    private static List<CoverageWarningDetails> parseWarnings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readArray().getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .map(JdbcUploadRepository::parseWarning)
                    .toList();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored coverage warnings must be a JSON string array", exception);
        }
    }

    private static CoverageWarningDetails parseWarning(String warning) {
        int separator = warning.lastIndexOf(':');
        if (separator < 1 || separator == warning.length() - 1) {
            return new CoverageWarningDetails(warning, 1);
        }
        try {
            return new CoverageWarningDetails(
                    warning.substring(0, separator),
                    Integer.parseInt(warning.substring(separator + 1)));
        } catch (NumberFormatException ignored) {
            return new CoverageWarningDetails(warning, 1);
        }
    }

    private static List<String> textArray(ResultSet resultSet, String column) throws SQLException {
        java.sql.Array array = resultSet.getArray(column);
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private Optional<QueuedUpload> find(String whereClause, Object... parameters) {
        String sql = """
                select u.id, u.tenant_id, u.repository_id, u.api_key_id,
                       u.commit_sha, u.branch, u.pull_request_number,
                       u.ci_provider, u.ci_build_id, u.ci_build_url,
                       u.flags, u.ignore_rules, u.config_snapshot_json::text as config_snapshot_json,
                       u.config_sha256, u.component, u.package_name, u.status,
                       u.idempotency_key, u.accepted_at, j.id as analysis_job_id
                from vericov.uploads u
                left join vericov.analysis_jobs j on j.upload_id = u.id
                """ + whereClause;
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapUpload(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load upload", exception);
        }
    }

    private static void insertUpload(Connection connection, QueuedUpload upload) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.uploads (
                    id, tenant_id, repository_id, api_key_id, commit_sha, branch,
                    pull_request_number, ci_provider, ci_build_id, ci_build_url,
                    flags, config_snapshot_json, config_sha256,
                    component, package_name, status, idempotency_key, accepted_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setObject(index++, upload.uploadId());
            statement.setObject(index++, upload.tenantId());
            statement.setObject(index++, upload.repositoryId());
            setNullableUuid(statement, index++, upload.apiKeyId().orElse(null));
            statement.setString(index++, upload.commitSha());
            statement.setString(index++, upload.branch());
            if (upload.pullRequestNumber() == null) {
                statement.setNull(index++, Types.INTEGER);
            } else {
                statement.setInt(index++, upload.pullRequestNumber());
            }
            statement.setString(index++, upload.ciProvider());
            statement.setString(index++, upload.ciBuildId());
            statement.setString(index++, upload.ciBuildUrl());
            statement.setArray(index++, connection.createArrayOf("text", upload.flags().toArray(String[]::new)));
            statement.setString(index++, upload.configSnapshotJson());
            statement.setString(index++, upload.configSha256());
            statement.setString(index++, upload.component().orElse(null));
            statement.setString(index++, upload.packageName().orElse(null));
            statement.setString(index++, databaseStatus(upload.status()));
            statement.setString(index++, upload.idempotencyKey());
            statement.setObject(index, OffsetDateTime.ofInstant(upload.acceptedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private static void insertArtifacts(
            Connection connection,
            UUID uploadId,
            List<StoredArtifact> artifacts) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_artifacts (
                    upload_id, name, kind, format, content_type, size_bytes,
                    storage_bucket, storage_path, sha256_hex
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (StoredArtifact artifact : artifacts) {
                statement.setObject(1, uploadId);
                statement.setString(2, artifact.name());
                statement.setString(3, artifact.kind().wireValue());
                statement.setString(4, artifact.format());
                statement.setString(5, artifact.contentType());
                statement.setLong(6, artifact.sizeBytes());
                statement.setString(7, artifact.storageBucket());
                statement.setString(8, artifact.storagePath());
                statement.setString(9, artifact.sha256());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertAnalysisJob(
            Connection connection,
            QueuedUpload upload,
            UUID analysisJobId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.analysis_jobs (
                    id, upload_id, tenant_id, repository_id, commit_sha, status
                )
                values (?, ?, ?, ?, ?, 'queued')
                """)) {
            statement.setObject(1, analysisJobId);
            statement.setObject(2, upload.uploadId());
            statement.setObject(3, upload.tenantId());
            statement.setObject(4, upload.repositoryId());
            statement.setString(5, upload.commitSha());
            statement.executeUpdate();
        }
    }

    private static void insertUploadEvent(
            Connection connection,
            QueuedUpload upload,
            UUID analysisJobId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.upload_events (
                    tenant_id, upload_id, event_type, payload, created_at
                )
                values (
                    ?, ?, 'upload.received',
                    jsonb_build_object(
                        'repository_id', ?::text,
                        'commit_sha', ?,
                        'analysis_job_id', ?::text
                    ),
                    ?
                )
                """)) {
            statement.setObject(1, upload.tenantId());
            statement.setObject(2, upload.uploadId());
            statement.setString(3, upload.repositoryId().toString());
            statement.setString(4, upload.commitSha());
            statement.setString(5, analysisJobId.toString());
            statement.setObject(6, OffsetDateTime.ofInstant(upload.acceptedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private static void enqueueAnalysisJob(Connection connection, UUID analysisJobId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select vericov.enqueue_coverage_analysis_job(?)")) {
            statement.setObject(1, analysisJobId);
            statement.execute();
        }
    }

    private static QueuedUpload mapUpload(ResultSet resultSet) throws SQLException {
        java.sql.Array flagsArray = resultSet.getArray("flags");
        String[] flags = flagsArray == null ? new String[0] : (String[]) flagsArray.getArray();
        String snapshotJson = resultSet.getString("config_snapshot_json");
        String snapshotHash = resultSet.getString("config_sha256");
        List<String> ignore = snapshotJson == null
                ? parseIgnoreRules(resultSet.getString("ignore_rules"))
                : ComponentConfigJson.parse(snapshotJson).ignore();
        if (snapshotJson == null && !ignore.isEmpty()) {
            ComponentConfigSnapshot snapshot = new ComponentConfigSnapshot(1, ignore, List.of());
            snapshotJson = ComponentConfigJson.canonicalJson(snapshot);
            snapshotHash = ComponentConfigJson.sha256(snapshot);
        }
        return new QueuedUpload(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                Optional.ofNullable(resultSet.getObject("api_key_id", UUID.class)),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                nullableInteger(resultSet, "pull_request_number"),
                resultSet.getString("ci_provider"),
                resultSet.getString("ci_build_id"),
                resultSet.getString("ci_build_url"),
                List.of(flags),
                ignore,
                snapshotJson,
                snapshotHash,
                Optional.ofNullable(resultSet.getString("component")),
                Optional.ofNullable(resultSet.getString("package_name")),
                uploadStatus(resultSet.getString("status")),
                resultSet.getString("idempotency_key"),
                resultSet.getObject("accepted_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(resultSet.getObject("analysis_job_id", UUID.class)));
    }

    private static List<String> parseIgnoreRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readArray().getValuesAs(JsonString.class).stream()
                    .map(JsonString::getString)
                    .toList();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored upload ignore rules must be a JSON string array", exception);
        }
    }

    private static void setNullableUuid(
            java.sql.PreparedStatement statement,
            int index,
            UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static CoverageMetricDetails metric(ResultSet resultSet, String prefix) throws SQLException {
        return new CoverageMetricDetails(
                resultSet.getLong(prefix + "_covered"),
                resultSet.getLong(prefix + "_total"));
    }

    private record ComponentProjection(
            List<ComponentCoverageDetails> components,
            List<CoverageWarningDetails> warnings) {
    }

    private record ComponentRow(
            String key,
            String parentKey,
            List<String> path,
            int depth,
            int position,
            String name,
            List<String> owners,
            Map<String, BigDecimal> effectiveGates,
            CoverageMetricDetails line,
            CoverageMetricDetails branchCoverage,
            CoverageMetricDetails function,
            CoverageMetricDetails statement,
            int directFileCount,
            int descendantFileCount,
            int gapCount,
            int debtCount,
            BigDecimal riskScoreTotal,
            String highestActiveRiskLevel) {
    }

    private static String databaseStatus(UploadStatus status) {
        return switch (status) {
            case ACCEPTED, QUEUED -> "queued";
            case PROCESSING -> "processing";
            case COMPLETED -> "processed";
            case FAILED -> "failed";
        };
    }

    private static UploadStatus uploadStatus(String status) {
        return switch (status) {
            case "queued" -> UploadStatus.QUEUED;
            case "processing" -> UploadStatus.PROCESSING;
            case "processed" -> UploadStatus.COMPLETED;
            case "failed", "rejected" -> UploadStatus.FAILED;
            default -> throw new IllegalStateException("Unsupported upload status: " + status);
        };
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database error.
        }
    }

    private static boolean isUniqueViolation(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if ("23505".equals(current.getSQLState())) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }
}
