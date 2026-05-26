package dev.vericov.agent.adapter.jdbc;

import dev.vericov.agent.application.AgentRunnerException;
import dev.vericov.agent.application.AgentTaskDetails;
import dev.vericov.agent.application.AgentTaskEvidence;
import dev.vericov.agent.application.AgentTaskSource;
import dev.vericov.agent.application.AgentTaskTarget;
import dev.vericov.agent.application.PolicyDecisionDetails;
import dev.vericov.agent.application.RequestedBy;
import dev.vericov.agent.application.port.AgentTaskRepository;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcAgentTaskRepository implements AgentTaskRepository {
    private final DataSource dataSource;
    private final AgentJsonCodec codec;

    public JdbcAgentTaskRepository(DataSource dataSource, AgentJsonCodec codec) {
        this.dataSource = dataSource;
        this.codec = codec;
    }

    @Override
    public AgentTaskDetails save(AgentTaskDetails task) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertRun(connection, task);
                insertTask(connection, task);
                insertPolicyDecision(connection, task.policyDecision());
                connection.commit();
                connection.setAutoCommit(autoCommit);
                return task;
            } catch (SQLException exception) {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save agent task", exception);
        }
    }

    @Override
    public Optional<AgentTaskDetails> findById(UUID taskId) {
        String sql = """
                SELECT
                    t.id,
                    t.tenant_id,
                    t.org_id,
                    t.repository_id,
                    t.agent_run_id,
                    t.task_type,
                    t.mode,
                    t.status,
                    t.payload,
                    t.result,
                    t.created_at,
                    t.updated_at,
                    r.pull_request_number,
                    r.commit_sha,
                    r.requested_by_type,
                    r.requested_by_id,
                    r.source_json,
                    r.target_json,
                    r.evidence_json,
                    pd.id AS policy_decision_id,
                    pd.decision,
                    pd.matched_policy_ids,
                    pd.action,
                    pd.resource,
                    pd.reason,
                    pd.created_at AS policy_decision_created_at
                FROM vericov.agent_tasks t
                JOIN vericov.agent_runs r ON r.id = t.agent_run_id
                JOIN LATERAL (
                    SELECT *
                    FROM vericov.policy_decisions
                    WHERE agent_task_id = t.id
                    ORDER BY created_at DESC
                    LIMIT 1
                ) pd ON true
                WHERE t.id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readTask(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read agent task", exception);
        }
    }

    @Override
    public AgentTaskDetails updatePolicyDecision(
            UUID taskId,
            PolicyDecisionDetails policyDecision,
            java.time.Instant updatedAt) {
        AgentTaskDetails existing = findById(taskId)
                .orElseThrow(() -> new AgentRunnerException("not_found", "Agent task not found"));
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insertPolicyDecision(connection, policyDecision);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE vericov.agent_tasks
                        SET updated_at = ?
                        WHERE id = ?
                        """)) {
                    statement.setTimestamp(1, timestamp(updatedAt));
                    statement.setObject(2, taskId);
                    statement.executeUpdate();
                }
                connection.commit();
                connection.setAutoCommit(autoCommit);
                return existing.withPolicyDecision(policyDecision, updatedAt);
            } catch (SQLException exception) {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update agent policy decision", exception);
        }
    }

    private void insertRun(Connection connection, AgentTaskDetails task) throws SQLException {
        String sql = """
                INSERT INTO vericov.agent_runs (
                    id, tenant_id, org_id, repository_id, pull_request_number, commit_sha,
                    task_type, mode, status, risk_level, requested_by_type, requested_by_id,
                    source_json, target_json, evidence_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, task.agentRunId());
            statement.setObject(index++, task.tenantId());
            statement.setObject(index++, task.orgId());
            statement.setObject(index++, task.repositoryId());
            if (task.source().pullRequestNumber() == null) {
                statement.setObject(index++, null);
            } else {
                statement.setInt(index++, task.source().pullRequestNumber());
            }
            statement.setString(index++, task.source().commitSha());
            statement.setString(index++, task.taskType());
            statement.setString(index++, task.mode());
            statement.setString(index++, "queued");
            statement.setString(index++, task.target().riskLevel());
            statement.setString(index++, task.requestedBy().type());
            statement.setString(index++, task.requestedBy().id());
            statement.setString(index++, codec.toJson(mapValue(task.payload().get("source"))));
            statement.setString(index++, codec.toJson(mapValue(task.payload().get("target"))));
            statement.setString(index++, codec.toJson(mapValue(task.payload().get("evidence"))));
            statement.setTimestamp(index++, timestamp(task.createdAt()));
            statement.setTimestamp(index, timestamp(task.updatedAt()));
            statement.executeUpdate();
        }
    }

    private void insertTask(Connection connection, AgentTaskDetails task) throws SQLException {
        String sql = """
                INSERT INTO vericov.agent_tasks (
                    id, tenant_id, org_id, agent_run_id, repository_id, task_type, mode,
                    status, payload, result, attempt_count, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 0, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, task.id());
            statement.setObject(index++, task.tenantId());
            statement.setObject(index++, task.orgId());
            statement.setObject(index++, task.agentRunId());
            statement.setObject(index++, task.repositoryId());
            statement.setString(index++, task.taskType());
            statement.setString(index++, task.mode());
            statement.setString(index++, task.status());
            statement.setString(index++, codec.toJson(task.payload()));
            statement.setString(index++, codec.toJson(task.result()));
            statement.setTimestamp(index++, timestamp(task.createdAt()));
            statement.setTimestamp(index, timestamp(task.updatedAt()));
            statement.executeUpdate();
        }
    }

    private void insertPolicyDecision(Connection connection, PolicyDecisionDetails decision) throws SQLException {
        String sql = """
                INSERT INTO vericov.policy_decisions (
                    id, tenant_id, agent_task_id, repository_id, decision,
                    matched_policy_ids, action, resource, reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, decision.id());
            statement.setObject(index++, decision.tenantId());
            statement.setObject(index++, decision.agentTaskId());
            statement.setObject(index++, decision.repositoryId());
            statement.setString(index++, decision.decision());
            statement.setArray(index++, connection.createArrayOf("uuid", decision.matchedPolicyIds().toArray()));
            statement.setString(index++, decision.action());
            statement.setString(index++, codec.toJson(decision.resource()));
            statement.setString(index++, decision.reason());
            statement.setTimestamp(index, timestamp(decision.createdAt()));
            statement.executeUpdate();
        }
    }

    private AgentTaskDetails readTask(ResultSet resultSet) throws SQLException {
        Map<String, Object> sourceJson = codec.fromJson(resultSet.getString("source_json"));
        Map<String, Object> targetJson = codec.fromJson(resultSet.getString("target_json"));
        Map<String, Object> evidenceJson = codec.fromJson(resultSet.getString("evidence_json"));
        UUID taskId = resultSet.getObject("id", UUID.class);
        return new AgentTaskDetails(
                taskId,
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("agent_run_id", UUID.class),
                resultSet.getString("task_type"),
                resultSet.getString("mode"),
                resultSet.getString("status"),
                readSource(sourceJson),
                readTarget(targetJson),
                readEvidence(evidenceJson),
                new RequestedBy(resultSet.getString("requested_by_type"), resultSet.getString("requested_by_id")),
                readPolicyDecision(resultSet, taskId),
                codec.fromJson(resultSet.getString("payload")),
                codec.fromJson(resultSet.getString("result")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private AgentTaskSource readSource(Map<String, Object> source) {
        return new AgentTaskSource(
                string(source, "type"),
                uuid(source, "coverage_report_id"),
                uuidList(source.get("coverage_gap_finding_ids")),
                integer(source.get("pull_request_number")),
                string(source, "commit_sha"),
                optionalString(source, "base_sha"),
                optionalString(source, "head_sha"));
    }

    private AgentTaskTarget readTarget(Map<String, Object> target) {
        return new AgentTaskTarget(
                string(target, "file_path"),
                integer(target.get("line_start")),
                integer(target.get("line_end")),
                string(target, "risk_level"),
                optionalUuid(target, "component_id"),
                stringList(target.get("owners")));
    }

    private AgentTaskEvidence readEvidence(Map<String, Object> evidence) {
        return new AgentTaskEvidence(
                string(evidence, "reason_code"),
                number(evidence.get("risk_score")).doubleValue(),
                string(evidence, "context_version"),
                mapValue(evidence.get("metadata")));
    }

    private PolicyDecisionDetails readPolicyDecision(ResultSet resultSet, UUID taskId) throws SQLException {
        return new PolicyDecisionDetails(
                resultSet.getObject("policy_decision_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                taskId,
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("decision"),
                uuidArray(resultSet.getArray("matched_policy_ids")),
                resultSet.getString("action"),
                codec.fromJson(resultSet.getString("resource")),
                resultSet.getString("reason"),
                resultSet.getTimestamp("policy_decision_created_at").toInstant());
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static AgentRunnerException databaseFailure(String message, SQLException exception) {
        return new AgentRunnerException("database_error", message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                copy.put(key, entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }

    private static String string(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value == null) {
            throw new AgentRunnerException("database_error", "Stored agent task is missing " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> map, String key) {
        return UUID.fromString(string(map, key));
    }

    private static UUID optionalUuid(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        return value == null ? null : UUID.fromString(value);
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static List<UUID> uuidList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> UUID.fromString(String.valueOf(item)))
                .toList();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .toList();
    }

    private static List<UUID> uuidArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) {
            return List.of();
        }
        return java.util.Arrays.stream(values)
                .map(value -> value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value)))
                .toList();
    }
}
