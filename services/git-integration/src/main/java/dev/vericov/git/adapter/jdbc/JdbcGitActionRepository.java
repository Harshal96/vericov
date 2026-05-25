package dev.vericov.git.adapter.jdbc;

import dev.vericov.git.application.GitBranchDetails;
import dev.vericov.git.application.GitCheckRunDetails;
import dev.vericov.git.application.GitIntegrationException;
import dev.vericov.git.application.GitPrCommentDetails;
import dev.vericov.git.application.GitPullRequestDetails;
import dev.vericov.git.application.GitWebhookEventDetails;
import dev.vericov.git.application.port.GitActionRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcGitActionRepository implements GitActionRepository {
    private final DataSource dataSource;
    private final GitJsonCodec codec;

    public JdbcGitActionRepository(DataSource dataSource, GitJsonCodec codec) {
        this.dataSource = dataSource;
        this.codec = codec;
    }

    @Override
    public Optional<GitWebhookEventDetails> findWebhookEvent(String providerKey, String deliveryId) {
        String sql = """
                SELECT * FROM vericov.git_webhook_events
                WHERE provider_key = ? AND delivery_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, providerKey);
            statement.setString(2, deliveryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readWebhookEvent(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read Git webhook event", exception);
        }
    }

    @Override
    public GitWebhookEventDetails saveWebhookEvent(GitWebhookEventDetails details) {
        String sql = """
                INSERT INTO vericov.git_webhook_events (
                    id, tenant_id, org_id, repository_id, connection_id, webhook_endpoint_id,
                    provider_key, event_type, delivery_id, signature_valid, payload_sha256,
                    payload, normalized_payload, status, error_json, received_at, processed_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (provider_key, delivery_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    org_id = EXCLUDED.org_id,
                    repository_id = EXCLUDED.repository_id,
                    connection_id = EXCLUDED.connection_id,
                    webhook_endpoint_id = EXCLUDED.webhook_endpoint_id,
                    event_type = EXCLUDED.event_type,
                    signature_valid = EXCLUDED.signature_valid,
                    payload_sha256 = EXCLUDED.payload_sha256,
                    payload = EXCLUDED.payload,
                    normalized_payload = EXCLUDED.normalized_payload,
                    status = EXCLUDED.status,
                    error_json = EXCLUDED.error_json,
                    processed_at = EXCLUDED.processed_at,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, details.id());
            setUuid(statement, index++, details.tenantId());
            setUuid(statement, index++, details.orgId());
            setUuid(statement, index++, details.repositoryId());
            setUuid(statement, index++, details.connectionId());
            setUuid(statement, index++, details.webhookEndpointId());
            statement.setString(index++, details.providerKey());
            statement.setString(index++, details.eventType());
            statement.setString(index++, details.deliveryId());
            statement.setBoolean(index++, details.signatureValid());
            statement.setString(index++, details.payloadSha256());
            statement.setString(index++, codec.toJson(details.payload()));
            statement.setString(index++, codec.toJson(details.normalizedPayload()));
            statement.setString(index++, details.status());
            statement.setString(index++, codec.toJson(details.error()));
            statement.setTimestamp(index++, timestamp(details.receivedAt()));
            statement.setTimestamp(index++, timestamp(details.processedAt()));
            statement.setTimestamp(index++, timestamp(details.createdAt()));
            statement.setTimestamp(index, timestamp(details.updatedAt()));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return readWebhookEvent(resultSet);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save Git webhook event", exception);
        }
    }

    @Override
    public Optional<GitPullRequestDetails> findPullRequest(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber) {
        String sql = """
                SELECT * FROM vericov.git_pull_requests
                WHERE tenant_id = ? AND repository_id = ? AND provider_key = ? AND number = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setString(3, providerKey);
            statement.setInt(4, pullRequestNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPullRequest(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read Git pull request", exception);
        }
    }

    @Override
    public GitPullRequestDetails savePullRequest(GitPullRequestDetails details) {
        String sql = """
                INSERT INTO vericov.git_pull_requests (
                    id, tenant_id, org_id, repository_id, provider_key, provider_pull_request_id,
                    number, title, author, base_branch, base_sha, head_branch, head_sha,
                    state, provider_url, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, repository_id, provider_key, number) DO UPDATE SET
                    provider_pull_request_id = EXCLUDED.provider_pull_request_id,
                    title = EXCLUDED.title,
                    author = EXCLUDED.author,
                    base_branch = EXCLUDED.base_branch,
                    base_sha = EXCLUDED.base_sha,
                    head_branch = EXCLUDED.head_branch,
                    head_sha = EXCLUDED.head_sha,
                    state = EXCLUDED.state,
                    provider_url = EXCLUDED.provider_url,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, details.id());
            statement.setObject(index++, details.tenantId());
            statement.setObject(index++, details.orgId());
            statement.setObject(index++, details.repositoryId());
            statement.setString(index++, details.providerKey());
            statement.setString(index++, details.providerPullRequestId());
            statement.setInt(index++, details.number());
            statement.setString(index++, details.title());
            statement.setString(index++, details.author());
            statement.setString(index++, details.baseBranch());
            statement.setString(index++, details.baseSha());
            statement.setString(index++, details.headBranch());
            statement.setString(index++, details.headSha());
            statement.setString(index++, details.state());
            statement.setString(index++, details.providerUrl());
            statement.setTimestamp(index++, timestamp(details.createdAt()));
            statement.setTimestamp(index, timestamp(details.updatedAt()));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return readPullRequest(resultSet);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save Git pull request", exception);
        }
    }

    @Override
    public Optional<GitCheckRunDetails> findCheckRunByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey) {
        String sql = """
                SELECT * FROM vericov.git_check_runs
                WHERE tenant_id = ? AND repository_id = ? AND provider_key = ? AND idempotency_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setString(3, providerKey);
            statement.setString(4, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readCheckRun(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read Git check run", exception);
        }
    }

    @Override
    public GitCheckRunDetails saveCheckRun(GitCheckRunDetails details) {
        String sql = """
                INSERT INTO vericov.git_check_runs (
                    id, tenant_id, org_id, repository_id, provider_key, commit_sha, name,
                    provider_check_id, status, conclusion, details_url, output_json,
                    idempotency_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (tenant_id, repository_id, provider_key, idempotency_key) DO UPDATE SET
                    commit_sha = EXCLUDED.commit_sha,
                    name = EXCLUDED.name,
                    provider_check_id = EXCLUDED.provider_check_id,
                    status = EXCLUDED.status,
                    conclusion = EXCLUDED.conclusion,
                    details_url = EXCLUDED.details_url,
                    output_json = EXCLUDED.output_json,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, details.id());
            statement.setObject(index++, details.tenantId());
            statement.setObject(index++, details.orgId());
            statement.setObject(index++, details.repositoryId());
            statement.setString(index++, details.providerKey());
            statement.setString(index++, details.commitSha());
            statement.setString(index++, details.name());
            statement.setString(index++, details.providerCheckId());
            statement.setString(index++, details.status());
            statement.setString(index++, details.conclusion());
            statement.setString(index++, details.detailsUrl());
            statement.setString(index++, codec.toJson(details.output()));
            statement.setString(index++, details.idempotencyKey());
            statement.setTimestamp(index++, timestamp(details.createdAt()));
            statement.setTimestamp(index, timestamp(details.updatedAt()));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return readCheckRun(resultSet);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save Git check run", exception);
        }
    }

    @Override
    public Optional<GitPrCommentDetails> findPrComment(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            int pullRequestNumber,
            String commentKey) {
        String sql = """
                SELECT * FROM vericov.git_pr_comments
                WHERE tenant_id = ? AND repository_id = ? AND provider_key = ?
                  AND pull_request_number = ? AND comment_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setString(3, providerKey);
            statement.setInt(4, pullRequestNumber);
            statement.setString(5, commentKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readPrComment(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read Git PR comment", exception);
        }
    }

    @Override
    public GitPrCommentDetails savePrComment(GitPrCommentDetails details) {
        String sql = """
                INSERT INTO vericov.git_pr_comments (
                    id, tenant_id, org_id, repository_id, provider_key, pull_request_number,
                    comment_key, provider_comment_id, body_hash, status, provider_url, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, repository_id, provider_key, pull_request_number, comment_key) DO UPDATE SET
                    provider_comment_id = EXCLUDED.provider_comment_id,
                    body_hash = EXCLUDED.body_hash,
                    status = EXCLUDED.status,
                    provider_url = EXCLUDED.provider_url,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, details.id());
            statement.setObject(index++, details.tenantId());
            statement.setObject(index++, details.orgId());
            statement.setObject(index++, details.repositoryId());
            statement.setString(index++, details.providerKey());
            statement.setInt(index++, details.pullRequestNumber());
            statement.setString(index++, details.commentKey());
            statement.setString(index++, details.providerCommentId());
            statement.setString(index++, details.bodyHash());
            statement.setString(index++, details.status());
            statement.setString(index++, details.providerUrl());
            statement.setTimestamp(index++, timestamp(details.createdAt()));
            statement.setTimestamp(index, timestamp(details.updatedAt()));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return readPrComment(resultSet);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save Git PR comment", exception);
        }
    }

    @Override
    public Optional<GitBranchDetails> findBranchByIdempotencyKey(
            UUID tenantId,
            UUID repositoryId,
            String providerKey,
            String idempotencyKey) {
        String sql = """
                SELECT * FROM vericov.git_branches
                WHERE tenant_id = ? AND repository_id = ? AND provider_key = ? AND idempotency_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, repositoryId);
            statement.setString(3, providerKey);
            statement.setString(4, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readBranch(resultSet));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to read Git branch", exception);
        }
    }

    @Override
    public GitBranchDetails saveBranch(GitBranchDetails details) {
        String sql = """
                INSERT INTO vericov.git_branches (
                    id, tenant_id, org_id, repository_id, provider_key, branch_name,
                    base_sha, provider_ref, status, idempotency_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, repository_id, provider_key, idempotency_key) DO UPDATE SET
                    branch_name = EXCLUDED.branch_name,
                    base_sha = EXCLUDED.base_sha,
                    provider_ref = EXCLUDED.provider_ref,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, details.id());
            statement.setObject(index++, details.tenantId());
            statement.setObject(index++, details.orgId());
            statement.setObject(index++, details.repositoryId());
            statement.setString(index++, details.providerKey());
            statement.setString(index++, details.branchName());
            statement.setString(index++, details.baseSha());
            statement.setString(index++, details.providerRef());
            statement.setString(index++, details.status());
            statement.setString(index++, details.idempotencyKey());
            statement.setTimestamp(index++, timestamp(details.createdAt()));
            statement.setTimestamp(index, timestamp(details.updatedAt()));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return readBranch(resultSet);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save Git branch", exception);
        }
    }

    private GitWebhookEventDetails readWebhookEvent(ResultSet resultSet) throws SQLException {
        return new GitWebhookEventDetails(
                uuid(resultSet, "id"),
                uuid(resultSet, "tenant_id"),
                uuid(resultSet, "org_id"),
                uuid(resultSet, "repository_id"),
                uuid(resultSet, "connection_id"),
                uuid(resultSet, "webhook_endpoint_id"),
                resultSet.getString("provider_key"),
                resultSet.getString("event_type"),
                resultSet.getString("delivery_id"),
                resultSet.getBoolean("signature_valid"),
                resultSet.getString("payload_sha256"),
                codec.fromJson(resultSet.getString("payload")),
                codec.fromJson(resultSet.getString("normalized_payload")),
                resultSet.getString("status"),
                codec.fromJson(resultSet.getString("error_json")),
                instant(resultSet, "received_at"),
                instant(resultSet, "processed_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private GitCheckRunDetails readCheckRun(ResultSet resultSet) throws SQLException {
        return new GitCheckRunDetails(
                uuid(resultSet, "id"),
                uuid(resultSet, "tenant_id"),
                uuid(resultSet, "org_id"),
                uuid(resultSet, "repository_id"),
                resultSet.getString("provider_key"),
                resultSet.getString("commit_sha"),
                resultSet.getString("name"),
                resultSet.getString("provider_check_id"),
                resultSet.getString("status"),
                resultSet.getString("conclusion"),
                resultSet.getString("details_url"),
                codec.fromJson(resultSet.getString("output_json")),
                resultSet.getString("idempotency_key"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private GitPullRequestDetails readPullRequest(ResultSet resultSet) throws SQLException {
        return new GitPullRequestDetails(
                uuid(resultSet, "id"),
                uuid(resultSet, "tenant_id"),
                uuid(resultSet, "org_id"),
                uuid(resultSet, "repository_id"),
                resultSet.getString("provider_key"),
                resultSet.getString("provider_pull_request_id"),
                resultSet.getInt("number"),
                resultSet.getString("title"),
                resultSet.getString("author"),
                resultSet.getString("base_branch"),
                resultSet.getString("base_sha"),
                resultSet.getString("head_branch"),
                resultSet.getString("head_sha"),
                resultSet.getString("state"),
                resultSet.getString("provider_url"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private GitPrCommentDetails readPrComment(ResultSet resultSet) throws SQLException {
        return new GitPrCommentDetails(
                uuid(resultSet, "id"),
                uuid(resultSet, "tenant_id"),
                uuid(resultSet, "org_id"),
                uuid(resultSet, "repository_id"),
                resultSet.getString("provider_key"),
                resultSet.getInt("pull_request_number"),
                resultSet.getString("comment_key"),
                resultSet.getString("provider_comment_id"),
                resultSet.getString("body_hash"),
                resultSet.getString("status"),
                resultSet.getString("provider_url"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private GitBranchDetails readBranch(ResultSet resultSet) throws SQLException {
        return new GitBranchDetails(
                uuid(resultSet, "id"),
                uuid(resultSet, "tenant_id"),
                uuid(resultSet, "org_id"),
                uuid(resultSet, "repository_id"),
                resultSet.getString("provider_key"),
                resultSet.getString("branch_name"),
                resultSet.getString("base_sha"),
                resultSet.getString("provider_ref"),
                resultSet.getString("status"),
                resultSet.getString("idempotency_key"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        statement.setObject(index, value);
    }

    private static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static Timestamp timestamp(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static GitIntegrationException databaseFailure(String message, SQLException exception) {
        return new GitIntegrationException("database_error", message + ": " + exception.getMessage());
    }
}
