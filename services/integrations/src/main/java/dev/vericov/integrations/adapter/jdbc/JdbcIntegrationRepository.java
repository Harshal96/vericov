package dev.vericov.integrations.adapter.jdbc;

import dev.vericov.integrations.application.IntegrationBindingDetails;
import dev.vericov.integrations.application.IntegrationConnectionDetails;
import dev.vericov.integrations.application.IntegrationCredentialDetails;
import dev.vericov.integrations.application.IntegrationEventDetails;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.IntegrationSyncStateDetails;
import dev.vericov.integrations.application.IntegrationWebhookEndpointDetails;
import dev.vericov.integrations.application.ResolvedIntegration;
import dev.vericov.integrations.application.port.IntegrationRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcIntegrationRepository implements IntegrationRepository {
    private final DataSource dataSource;
    private final IntegrationJsonCodec codec;

    public JdbcIntegrationRepository(DataSource dataSource, IntegrationJsonCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public List<IntegrationConnectionDetails> listConnections(UUID tenantId, UUID orgId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider_key,
                            integration_type,
                            display_name,
                            external_account_id,
                            external_account_name,
                            status,
                            config_json,
                            created_by,
                            last_verified_at,
                            created_at,
                            updated_at
                        from vericov.integration_connections
                        where tenant_id = ?
                          and org_id = ?
                        order by display_name, created_at, id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            try (var resultSet = statement.executeQuery()) {
                List<IntegrationConnectionDetails> connections = new ArrayList<>();
                while (resultSet.next()) {
                    connections.add(readConnection(resultSet));
                }
                return List.copyOf(connections);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list integration connections", exception);
        }
    }

    @Override
    public Optional<IntegrationConnectionDetails> findConnection(UUID tenantId, UUID orgId, UUID connectionId) {
        try (var connection = dataSource.getConnection()) {
            return findConnection(connection, tenantId, orgId, connectionId);
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find integration connection", exception);
        }
    }

    @Override
    public Optional<IntegrationConnectionDetails> findActiveConnection(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String externalAccountId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider_key,
                            integration_type,
                            display_name,
                            external_account_id,
                            external_account_name,
                            status,
                            config_json,
                            created_by,
                            last_verified_at,
                            created_at,
                            updated_at
                        from vericov.integration_connections
                        where tenant_id = ?
                          and org_id = ?
                          and provider_key = ?
                          and external_account_id = ?
                          and status = 'active'
                        order by created_at, id
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setString(3, providerKey);
            statement.setString(4, externalAccountId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readConnection(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find active integration connection", exception);
        }
    }

    @Override
    public IntegrationConnectionDetails saveConnection(IntegrationConnectionDetails integrationConnection) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (connectionIdExists(connection, integrationConnection.id())) {
                    throw new IntegrationException("conflict", "Integration connection already exists");
                }
                if ("active".equals(integrationConnection.status())
                        && activeConnectionExists(connection, integrationConnection, null)) {
                    throw new IntegrationException("conflict", "Integration connection already exists");
                }
                IntegrationConnectionDetails saved;
                try (var statement = connection.prepareStatement("""
                        insert into vericov.integration_connections (
                            id,
                            tenant_id,
                            org_id,
                            provider_key,
                            integration_type,
                            display_name,
                            external_account_id,
                            external_account_name,
                            status,
                            config_json,
                            created_by,
                            last_verified_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                        returning
                            id,
                            tenant_id,
                            org_id,
                            provider_key,
                            integration_type,
                            display_name,
                            external_account_id,
                            external_account_name,
                            status,
                            config_json,
                            created_by,
                            last_verified_at,
                            created_at,
                            updated_at
                        """)) {
                    setConnection(statement, integrationConnection);
                    try (var resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Integration connection insert did not return a row");
                        }
                        saved = readConnection(resultSet);
                    }
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to save integration connection",
                        exception,
                        "Integration connection already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save integration connection", exception);
        }
    }

    @Override
    public IntegrationConnectionDetails updateConnection(
            IntegrationConnectionDetails integrationConnection,
            Instant expectedUpdatedAt) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if ("active".equals(integrationConnection.status())
                        && activeConnectionExists(connection, integrationConnection, integrationConnection.id())) {
                    throw new IntegrationException("conflict", "Integration connection already exists");
                }
                Optional<IntegrationConnectionDetails> updated;
                try (var statement = connection.prepareStatement("""
                        update vericov.integration_connections
                        set provider_key = ?,
                            integration_type = ?,
                            display_name = ?,
                            external_account_id = ?,
                            external_account_name = ?,
                            status = ?,
                            config_json = ?::jsonb,
                            last_verified_at = ?,
                            updated_at = ?
                        where tenant_id = ?
                          and org_id = ?
                          and id = ?
                          and updated_at = ?
                        returning
                            id,
                            tenant_id,
                            org_id,
                            provider_key,
                            integration_type,
                            display_name,
                            external_account_id,
                            external_account_name,
                            status,
                            config_json,
                            created_by,
                            last_verified_at,
                            created_at,
                            updated_at
                        """)) {
                    statement.setString(1, integrationConnection.providerKey());
                    statement.setString(2, integrationConnection.integrationType());
                    statement.setString(3, integrationConnection.displayName());
                    statement.setString(4, integrationConnection.externalAccountId());
                    statement.setString(5, integrationConnection.externalAccountName());
                    statement.setString(6, integrationConnection.status());
                    statement.setString(7, codec.toJsonObject(integrationConnection.config()));
                    setNullableInstant(statement, 8, integrationConnection.lastVerifiedAt());
                    statement.setObject(9, utc(nextDatabaseUpdatedAt(
                            integrationConnection.updatedAt(),
                            expectedUpdatedAt)));
                    statement.setObject(10, integrationConnection.tenantId());
                    statement.setObject(11, integrationConnection.orgId());
                    statement.setObject(12, integrationConnection.id());
                    setNullableInstant(statement, 13, expectedUpdatedAt);
                    try (var resultSet = statement.executeQuery()) {
                        updated = resultSet.next() ? Optional.of(readConnection(resultSet)) : Optional.empty();
                    }
                }
                if (updated.isEmpty()) {
                    if (findConnection(connection, integrationConnection.tenantId(), integrationConnection.orgId(),
                            integrationConnection.id()).isEmpty()) {
                        throw new IntegrationException("not_found", "Integration connection not found");
                    }
                    throw new IntegrationException("conflict", "Integration connection was modified");
                }
                connection.commit();
                return updated.orElseThrow();
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to update integration connection",
                        exception,
                        "Integration connection already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update integration connection", exception);
        }
    }

    @Override
    public List<IntegrationBindingDetails> listBindings(UUID tenantId, UUID orgId, UUID connectionId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            bindings.id,
                            bindings.tenant_id,
                            bindings.connection_id,
                            bindings.scope_type,
                            bindings.scope_id,
                            bindings.capabilities,
                            bindings.config_json,
                            bindings.status,
                            bindings.created_at,
                            bindings.updated_at
                        from vericov.integration_bindings bindings
                        join vericov.integration_connections connections
                          on connections.tenant_id = bindings.tenant_id
                         and connections.id = bindings.connection_id
                        where connections.tenant_id = ?
                          and connections.org_id = ?
                          and connections.id = ?
                        order by bindings.scope_type, bindings.scope_id, bindings.created_at, bindings.id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            try (var resultSet = statement.executeQuery()) {
                List<IntegrationBindingDetails> bindings = new ArrayList<>();
                while (resultSet.next()) {
                    bindings.add(readBinding(resultSet));
                }
                return List.copyOf(bindings);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list integration bindings", exception);
        }
    }

    @Override
    public Optional<IntegrationBindingDetails> findBinding(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String scopeType,
            UUID scopeId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            bindings.id,
                            bindings.tenant_id,
                            bindings.connection_id,
                            bindings.scope_type,
                            bindings.scope_id,
                            bindings.capabilities,
                            bindings.config_json,
                            bindings.status,
                            bindings.created_at,
                            bindings.updated_at
                        from vericov.integration_bindings bindings
                        join vericov.integration_connections connections
                          on connections.tenant_id = bindings.tenant_id
                         and connections.id = bindings.connection_id
                        where connections.tenant_id = ?
                          and connections.org_id = ?
                          and connections.id = ?
                          and bindings.scope_type = ?
                          and bindings.scope_id = ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            statement.setString(4, scopeType);
            statement.setObject(5, scopeId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readBinding(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find integration binding", exception);
        }
    }

    @Override
    public IntegrationBindingDetails upsertBinding(IntegrationBindingDetails binding, Instant expectedUpdatedAt) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!connectionExists(connection, binding.tenantId(), binding.connectionId())) {
                    throw new IntegrationException("not_found", "Integration connection not found");
                }
                Optional<IntegrationBindingDetails> current = findBindingForUpdate(connection, binding);
                IntegrationBindingDetails saved;
                if (current.isEmpty()) {
                    if (expectedUpdatedAt != null) {
                        throw new IntegrationException("not_found", "Integration binding not found");
                    }
                    if (bindingIdExists(connection, binding.tenantId(), binding.id())) {
                        throw new IntegrationException("conflict", "Integration binding already exists");
                    }
                    saved = insertBinding(connection, binding);
                } else {
                    IntegrationBindingDetails existing = current.orElseThrow();
                    if (expectedUpdatedAt == null) {
                        throw new IntegrationException("conflict", "Integration binding already exists");
                    }
                    if (!existing.updatedAt().equals(expectedUpdatedAt)) {
                        throw new IntegrationException("conflict", "Integration binding was modified");
                    }
                    Instant updatedAt = binding.updatedAt().isAfter(existing.updatedAt())
                            ? binding.updatedAt()
                            // PostgreSQL timestamptz stores microseconds, so one nanosecond can collapse on write.
                            : existing.updatedAt().plusNanos(1_000);
                    saved = updateBinding(connection, existing, binding, updatedAt);
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to upsert integration binding",
                        exception,
                        "Integration binding already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to upsert integration binding", exception);
        }
    }

    @Override
    public IntegrationCredentialDetails saveCredential(IntegrationCredentialDetails credential) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!connectionExists(connection, credential.tenantId(), credential.connectionId())) {
                    throw new IntegrationException("not_found", "Integration connection not found");
                }
                if (credentialIdExists(connection, credential.id())) {
                    throw new IntegrationException("conflict", "Integration credential already exists");
                }
                if (activeCredentialExists(connection, credential)) {
                    throw new IntegrationException("conflict", "Integration credential already exists");
                }
                IntegrationCredentialDetails saved;
                try (var statement = connection.prepareStatement("""
                        insert into vericov.integration_credentials (
                            id,
                            tenant_id,
                            connection_id,
                            credential_kind,
                            secret_ref,
                            key_version,
                            status,
                            expires_at,
                            last_rotated_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning
                            id,
                            tenant_id,
                            connection_id,
                            credential_kind,
                            secret_ref,
                            key_version,
                            status,
                            expires_at,
                            last_rotated_at,
                            created_at,
                            updated_at
                        """)) {
                    statement.setObject(1, credential.id());
                    statement.setObject(2, credential.tenantId());
                    statement.setObject(3, credential.connectionId());
                    statement.setString(4, credential.credentialKind());
                    statement.setString(5, credential.secretRef());
                    statement.setInt(6, credential.keyVersion());
                    statement.setString(7, credential.status());
                    setNullableInstant(statement, 8, credential.expiresAt());
                    setNullableInstant(statement, 9, credential.lastRotatedAt());
                    statement.setObject(10, utc(credential.createdAt()));
                    statement.setObject(11, utc(credential.updatedAt()));
                    try (var resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Integration credential insert did not return a row");
                        }
                        saved = readCredential(resultSet);
                    }
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to save integration credential",
                        exception,
                        "Integration credential already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save integration credential", exception);
        }
    }

    @Override
    public List<IntegrationCredentialDetails> listCredentials(UUID tenantId, UUID orgId, UUID connectionId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            credentials.id,
                            credentials.tenant_id,
                            credentials.connection_id,
                            credentials.credential_kind,
                            credentials.secret_ref,
                            credentials.key_version,
                            credentials.status,
                            credentials.expires_at,
                            credentials.last_rotated_at,
                            credentials.created_at,
                            credentials.updated_at
                        from vericov.integration_credentials credentials
                        join vericov.integration_connections connections
                          on connections.tenant_id = credentials.tenant_id
                         and connections.id = credentials.connection_id
                        where connections.tenant_id = ?
                          and connections.org_id = ?
                          and connections.id = ?
                        order by credentials.credential_kind, credentials.created_at, credentials.id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            try (var resultSet = statement.executeQuery()) {
                List<IntegrationCredentialDetails> credentials = new ArrayList<>();
                while (resultSet.next()) {
                    credentials.add(readCredential(resultSet));
                }
                return List.copyOf(credentials);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list integration credentials", exception);
        }
    }

    @Override
    public Optional<IntegrationCredentialDetails> findActiveCredential(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            String credentialKind) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            credentials.id,
                            credentials.tenant_id,
                            credentials.connection_id,
                            credentials.credential_kind,
                            credentials.secret_ref,
                            credentials.key_version,
                            credentials.status,
                            credentials.expires_at,
                            credentials.last_rotated_at,
                            credentials.created_at,
                            credentials.updated_at
                        from vericov.integration_credentials credentials
                        join vericov.integration_connections connections
                          on connections.tenant_id = credentials.tenant_id
                         and connections.id = credentials.connection_id
                        where connections.tenant_id = ?
                          and connections.org_id = ?
                          and connections.id = ?
                          and credentials.credential_kind = ?
                          and credentials.status = 'active'
                        order by credentials.created_at, credentials.id
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            statement.setString(4, credentialKind);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCredential(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find active integration credential", exception);
        }
    }

    @Override
    public IntegrationWebhookEndpointDetails saveWebhookEndpoint(IntegrationWebhookEndpointDetails endpoint) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                IntegrationConnectionDetails integrationConnection = findConnection(
                                connection,
                                endpoint.tenantId(),
                                endpoint.orgId(),
                                endpoint.connectionId())
                        .orElseThrow(() -> new IntegrationException(
                                "not_found",
                                "Integration connection not found"));
                if (!integrationConnection.providerKey().equals(endpoint.providerKey())) {
                    throw new IntegrationException("validation_error", "provider_key does not match connection");
                }
                IntegrationWebhookEndpointDetails saved;
                try (var statement = connection.prepareStatement("""
                        insert into vericov.integration_webhook_endpoints (
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            provider_key,
                            external_webhook_id,
                            endpoint_url,
                            event_types,
                            status,
                            signing_secret_ref,
                            config_json,
                            last_delivery_json,
                            last_delivered_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                        returning
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            provider_key,
                            external_webhook_id,
                            endpoint_url,
                            event_types,
                            status,
                            signing_secret_ref,
                            config_json,
                            last_delivery_json,
                            last_delivered_at,
                            created_at,
                            updated_at
                        """)) {
                    setWebhookEndpoint(statement, connection, endpoint);
                    try (var resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Integration webhook endpoint insert did not return a row");
                        }
                        saved = readWebhookEndpoint(resultSet);
                    }
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to save integration webhook endpoint",
                        exception,
                        "Integration webhook endpoint already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save integration webhook endpoint", exception);
        }
    }

    @Override
    public List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(UUID tenantId, UUID orgId, UUID connectionId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            endpoints.id,
                            endpoints.tenant_id,
                            endpoints.org_id,
                            endpoints.connection_id,
                            endpoints.provider_key,
                            endpoints.external_webhook_id,
                            endpoints.endpoint_url,
                            endpoints.event_types,
                            endpoints.status,
                            endpoints.signing_secret_ref,
                            endpoints.config_json,
                            endpoints.last_delivery_json,
                            endpoints.last_delivered_at,
                            endpoints.created_at,
                            endpoints.updated_at
                        from vericov.integration_webhook_endpoints endpoints
                        where endpoints.tenant_id = ?
                          and endpoints.org_id = ?
                          and endpoints.connection_id = ?
                        order by endpoints.provider_key, endpoints.created_at, endpoints.id
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            try (var resultSet = statement.executeQuery()) {
                List<IntegrationWebhookEndpointDetails> endpoints = new ArrayList<>();
                while (resultSet.next()) {
                    endpoints.add(readWebhookEndpoint(resultSet));
                }
                return List.copyOf(endpoints);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list integration webhook endpoints", exception);
        }
    }

    @Override
    public Optional<IntegrationWebhookEndpointDetails> findWebhookEndpoint(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            UUID endpointId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            provider_key,
                            external_webhook_id,
                            endpoint_url,
                            event_types,
                            status,
                            signing_secret_ref,
                            config_json,
                            last_delivery_json,
                            last_delivered_at,
                            created_at,
                            updated_at
                        from vericov.integration_webhook_endpoints
                        where tenant_id = ?
                          and org_id = ?
                          and connection_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            statement.setObject(4, endpointId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readWebhookEndpoint(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find integration webhook endpoint", exception);
        }
    }

    @Override
    public Optional<ResolvedIntegration> resolve(
            UUID tenantId,
            UUID orgId,
            String providerKey,
            String scopeType,
            UUID scopeId,
            String capability,
            String credentialKind) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            connections.id as connection_id,
                            connections.tenant_id as connection_tenant_id,
                            connections.org_id as connection_org_id,
                            connections.provider_key as connection_provider_key,
                            connections.integration_type as connection_integration_type,
                            connections.display_name as connection_display_name,
                            connections.external_account_id as connection_external_account_id,
                            connections.external_account_name as connection_external_account_name,
                            connections.status as connection_status,
                            connections.config_json as connection_config_json,
                            connections.created_by as connection_created_by,
                            connections.last_verified_at as connection_last_verified_at,
                            connections.created_at as connection_created_at,
                            connections.updated_at as connection_updated_at,
                            bindings.id as binding_id,
                            bindings.tenant_id as binding_tenant_id,
                            bindings.connection_id as binding_connection_id,
                            bindings.scope_type as binding_scope_type,
                            bindings.scope_id as binding_scope_id,
                            bindings.capabilities as binding_capabilities,
                            bindings.config_json as binding_config_json,
                            bindings.status as binding_status,
                            bindings.created_at as binding_created_at,
                            bindings.updated_at as binding_updated_at,
                            credentials.credential_kind as resolved_credential_kind
                        from vericov.integration_connections connections
                        join vericov.integration_bindings bindings
                          on bindings.tenant_id = connections.tenant_id
                         and bindings.connection_id = connections.id
                        join vericov.integration_credentials credentials
                          on credentials.tenant_id = connections.tenant_id
                         and credentials.connection_id = connections.id
                        where connections.tenant_id = ?
                          and connections.org_id = ?
                          and connections.provider_key = ?
                          and connections.status = 'active'
                          and bindings.scope_type = ?
                          and bindings.scope_id = ?
                          and bindings.status = 'active'
                          and ? = any(bindings.capabilities)
                          and credentials.credential_kind = ?
                          and credentials.status = 'active'
                        order by bindings.created_at, bindings.id
                        limit 2
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setString(3, providerKey);
            statement.setString(4, scopeType);
            statement.setObject(5, scopeId);
            statement.setString(6, capability);
            statement.setString(7, credentialKind);
            try (var resultSet = statement.executeQuery()) {
                List<ResolvedIntegration> matches = new ArrayList<>();
                while (resultSet.next()) {
                    matches.add(new ResolvedIntegration(readConnection(resultSet, "connection_"),
                            readBinding(resultSet, "binding_"),
                            resultSet.getString("resolved_credential_kind")));
                }
                if (matches.size() > 1) {
                    throw new IntegrationException("conflict", "Integration resolution is ambiguous");
                }
                return matches.stream().findFirst();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to resolve integration", exception);
        }
    }

    @Override
    public IntegrationSyncStateDetails upsertSyncState(IntegrationSyncStateDetails syncState) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (findConnection(
                        connection,
                        syncState.tenantId(),
                        syncState.orgId(),
                        syncState.connectionId()).isEmpty()) {
                    throw new IntegrationException("not_found", "Integration connection not found");
                }
                IntegrationSyncStateDetails saved;
                try (var statement = connection.prepareStatement("""
                        insert into vericov.integration_sync_states (
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            sync_type,
                            scope_type,
                            scope_id,
                            status,
                            cursor_json,
                            checkpoint_json,
                            last_error_json,
                            last_started_at,
                            last_completed_at,
                            next_run_at,
                            lease_expires_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id, connection_id, scope_type, scope_id, sync_type)
                        do update set
                            org_id = excluded.org_id,
                            status = excluded.status,
                            cursor_json = excluded.cursor_json,
                            checkpoint_json = excluded.checkpoint_json,
                            last_error_json = excluded.last_error_json,
                            last_started_at = excluded.last_started_at,
                            last_completed_at = excluded.last_completed_at,
                            next_run_at = excluded.next_run_at,
                            lease_expires_at = excluded.lease_expires_at,
                            updated_at = case
                                when excluded.updated_at > integration_sync_states.updated_at
                                    then excluded.updated_at
                                else integration_sync_states.updated_at + interval '1 microsecond'
                            end
                        returning
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            sync_type,
                            scope_type,
                            scope_id,
                            status,
                            cursor_json,
                            checkpoint_json,
                            last_error_json,
                            last_started_at,
                            last_completed_at,
                            next_run_at,
                            lease_expires_at,
                            created_at,
                            updated_at
                        """)) {
                    statement.setObject(1, syncState.id());
                    statement.setObject(2, syncState.tenantId());
                    statement.setObject(3, syncState.orgId());
                    statement.setObject(4, syncState.connectionId());
                    statement.setString(5, syncState.syncType());
                    statement.setString(6, syncState.scopeType());
                    statement.setObject(7, syncState.scopeId());
                    statement.setString(8, syncState.status());
                    statement.setString(9, codec.toJsonObject(syncState.cursor()));
                    statement.setString(10, codec.toJsonObject(syncState.checkpoint()));
                    statement.setString(11, codec.toJsonObject(syncState.lastError()));
                    setNullableInstant(statement, 12, syncState.lastStartedAt());
                    setNullableInstant(statement, 13, syncState.lastCompletedAt());
                    setNullableInstant(statement, 14, syncState.nextRunAt());
                    setNullableInstant(statement, 15, syncState.leaseExpiresAt());
                    statement.setObject(16, utc(syncState.createdAt()));
                    statement.setObject(17, utc(syncState.updatedAt()));
                    try (var resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Integration sync state upsert did not return a row");
                        }
                        saved = readSyncState(resultSet);
                    }
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to upsert integration sync state",
                        exception,
                        "Integration sync state already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to upsert integration sync state", exception);
        }
    }

    @Override
    public IntegrationEventDetails recordEvent(IntegrationEventDetails event) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (eventIdExists(connection, event.id())) {
                    throw new IntegrationException("conflict", "Integration event already exists");
                }
                if (event.connectionId() != null) {
                    IntegrationConnectionDetails integrationConnection = findConnection(
                                    connection,
                                    event.tenantId(),
                                    event.orgId(),
                                    event.connectionId())
                            .orElseThrow(() -> new IntegrationException(
                                    "not_found",
                                    "Integration connection not found"));
                    if (!integrationConnection.providerKey().equals(event.providerKey())) {
                        throw new IntegrationException("validation_error", "provider_key does not match connection");
                    }
                }
                IntegrationEventDetails saved;
                try (var statement = connection.prepareStatement("""
                        insert into vericov.integration_events (
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            webhook_endpoint_id,
                            provider_key,
                            event_type,
                            external_event_id,
                            scope_type,
                            scope_id,
                            status,
                            payload,
                            error_json,
                            received_at,
                            processed_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                        returning
                            id,
                            tenant_id,
                            org_id,
                            connection_id,
                            webhook_endpoint_id,
                            provider_key,
                            event_type,
                            external_event_id,
                            scope_type,
                            scope_id,
                            status,
                            payload,
                            error_json,
                            received_at,
                            processed_at,
                            created_at,
                            updated_at
                        """)) {
                    statement.setObject(1, event.id());
                    statement.setObject(2, event.tenantId());
                    statement.setObject(3, event.orgId());
                    statement.setObject(4, event.connectionId());
                    statement.setObject(5, event.webhookEndpointId());
                    statement.setString(6, event.providerKey());
                    statement.setString(7, event.eventType());
                    statement.setString(8, event.externalEventId());
                    statement.setString(9, event.scopeType());
                    statement.setObject(10, event.scopeId());
                    statement.setString(11, event.status());
                    statement.setString(12, codec.toJsonObject(event.payload()));
                    statement.setString(13, codec.toJsonObject(event.error()));
                    statement.setObject(14, utc(event.receivedAt()));
                    setNullableInstant(statement, 15, event.processedAt());
                    statement.setObject(16, utc(event.createdAt()));
                    statement.setObject(17, utc(event.updatedAt()));
                    try (var resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("Integration event insert did not return a row");
                        }
                        saved = readEvent(resultSet);
                    }
                }
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw mapIntegrityFailure(
                        "Failed to record integration event",
                        exception,
                        "Integration event already exists");
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to record integration event", exception);
        }
    }

    private Optional<IntegrationConnectionDetails> findConnection(
            Connection connection,
            UUID tenantId,
            UUID orgId,
            UUID connectionId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select
                    id,
                    tenant_id,
                    org_id,
                    provider_key,
                    integration_type,
                    display_name,
                    external_account_id,
                    external_account_name,
                    status,
                    config_json,
                    created_by,
                    last_verified_at,
                    created_at,
                    updated_at
                from vericov.integration_connections
                where tenant_id = ?
                  and org_id = ?
                  and id = ?
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, connectionId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readConnection(resultSet)) : Optional.empty();
            }
        }
    }

    private boolean activeConnectionExists(
            Connection connection,
            IntegrationConnectionDetails integrationConnection,
            UUID excludedConnectionId) throws SQLException {
        String sql = excludedConnectionId == null
                ? """
                select 1
                from vericov.integration_connections
                where tenant_id = ?
                  and org_id = ?
                  and provider_key = ?
                  and external_account_id = ?
                  and status = 'active'
                limit 1
                """
                : """
                select 1
                from vericov.integration_connections
                where tenant_id = ?
                  and org_id = ?
                  and provider_key = ?
                  and external_account_id = ?
                  and status = 'active'
                  and id <> ?
                limit 1
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, integrationConnection.tenantId());
            statement.setObject(2, integrationConnection.orgId());
            statement.setString(3, integrationConnection.providerKey());
            statement.setString(4, integrationConnection.externalAccountId());
            if (excludedConnectionId != null) {
                statement.setObject(5, excludedConnectionId);
            }
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean connectionIdExists(Connection connection, UUID connectionId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_connections
                where id = ?
                limit 1
                """)) {
            statement.setObject(1, connectionId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean connectionExists(Connection connection, UUID tenantId, UUID connectionId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_connections
                where tenant_id = ?
                  and id = ?
                limit 1
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, connectionId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Optional<IntegrationBindingDetails> findBindingForUpdate(
            Connection connection,
            IntegrationBindingDetails binding) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select
                    id,
                    tenant_id,
                    connection_id,
                    scope_type,
                    scope_id,
                    capabilities,
                    config_json,
                    status,
                    created_at,
                    updated_at
                from vericov.integration_bindings
                where tenant_id = ?
                  and connection_id = ?
                  and scope_type = ?
                  and scope_id = ?
                for update
                """)) {
            statement.setObject(1, binding.tenantId());
            statement.setObject(2, binding.connectionId());
            statement.setString(3, binding.scopeType());
            statement.setObject(4, binding.scopeId());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readBinding(resultSet)) : Optional.empty();
            }
        }
    }

    private boolean bindingIdExists(Connection connection, UUID tenantId, UUID bindingId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_bindings
                where tenant_id = ?
                  and id = ?
                limit 1
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, bindingId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private IntegrationBindingDetails insertBinding(Connection connection, IntegrationBindingDetails binding)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.integration_bindings (
                    id,
                    tenant_id,
                    connection_id,
                    scope_type,
                    scope_id,
                    capabilities,
                    config_json,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                returning
                    id,
                    tenant_id,
                    connection_id,
                    scope_type,
                    scope_id,
                    capabilities,
                    config_json,
                    status,
                    created_at,
                    updated_at
                """)) {
            statement.setObject(1, binding.id());
            statement.setObject(2, binding.tenantId());
            statement.setObject(3, binding.connectionId());
            statement.setString(4, binding.scopeType());
            statement.setObject(5, binding.scopeId());
            statement.setArray(6, codec.textArray(connection, binding.capabilities()));
            statement.setString(7, codec.toJsonObject(binding.config()));
            statement.setString(8, binding.status());
            statement.setObject(9, utc(binding.createdAt()));
            statement.setObject(10, utc(binding.updatedAt()));
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Integration binding insert did not return a row");
                }
                return readBinding(resultSet);
            }
        }
    }

    private IntegrationBindingDetails updateBinding(
            Connection connection,
            IntegrationBindingDetails existing,
            IntegrationBindingDetails binding,
            Instant updatedAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                update vericov.integration_bindings
                set capabilities = ?,
                    config_json = ?::jsonb,
                    status = ?,
                    updated_at = ?
                where tenant_id = ?
                  and id = ?
                returning
                    id,
                    tenant_id,
                    connection_id,
                    scope_type,
                    scope_id,
                    capabilities,
                    config_json,
                    status,
                    created_at,
                    updated_at
                """)) {
            statement.setArray(1, codec.textArray(connection, binding.capabilities()));
            statement.setString(2, codec.toJsonObject(binding.config()));
            statement.setString(3, binding.status());
            statement.setObject(4, utc(updatedAt));
            statement.setObject(5, existing.tenantId());
            statement.setObject(6, existing.id());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IntegrationException("not_found", "Integration binding not found");
                }
                return readBinding(resultSet);
            }
        }
    }

    private boolean credentialIdExists(Connection connection, UUID credentialId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_credentials
                where id = ?
                limit 1
                """)) {
            statement.setObject(1, credentialId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean eventIdExists(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_events
                where id = ?
                limit 1
                """)) {
            statement.setObject(1, eventId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean activeCredentialExists(Connection connection, IntegrationCredentialDetails credential)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                select 1
                from vericov.integration_credentials
                where tenant_id = ?
                  and connection_id = ?
                  and credential_kind = ?
                  and status = 'active'
                limit 1
                """)) {
            statement.setObject(1, credential.tenantId());
            statement.setObject(2, credential.connectionId());
            statement.setString(3, credential.credentialKind());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void setConnection(PreparedStatement statement, IntegrationConnectionDetails connection)
            throws SQLException {
        statement.setObject(1, connection.id());
        statement.setObject(2, connection.tenantId());
        statement.setObject(3, connection.orgId());
        statement.setString(4, connection.providerKey());
        statement.setString(5, connection.integrationType());
        statement.setString(6, connection.displayName());
        statement.setString(7, connection.externalAccountId());
        statement.setString(8, connection.externalAccountName());
        statement.setString(9, connection.status());
        statement.setString(10, codec.toJsonObject(connection.config()));
        statement.setObject(11, connection.createdBy());
        setNullableInstant(statement, 12, connection.lastVerifiedAt());
        statement.setObject(13, utc(connection.createdAt()));
        statement.setObject(14, utc(connection.updatedAt()));
    }

    private void setWebhookEndpoint(
            PreparedStatement statement,
            Connection connection,
            IntegrationWebhookEndpointDetails endpoint) throws SQLException {
        statement.setObject(1, endpoint.id());
        statement.setObject(2, endpoint.tenantId());
        statement.setObject(3, endpoint.orgId());
        statement.setObject(4, endpoint.connectionId());
        statement.setString(5, endpoint.providerKey());
        statement.setString(6, endpoint.externalWebhookId());
        statement.setString(7, endpoint.endpointUrl());
        statement.setArray(8, codec.textArray(connection, endpoint.eventTypes()));
        statement.setString(9, endpoint.status());
        statement.setString(10, endpoint.signingSecretRef());
        statement.setString(11, codec.toJsonObject(endpoint.config()));
        statement.setString(12, codec.toJsonObject(endpoint.lastDelivery()));
        setNullableInstant(statement, 13, endpoint.lastDeliveredAt());
        statement.setObject(14, utc(endpoint.createdAt()));
        statement.setObject(15, utc(endpoint.updatedAt()));
    }

    private IntegrationConnectionDetails readConnection(ResultSet resultSet) throws SQLException {
        return readConnection(resultSet, "");
    }

    private IntegrationConnectionDetails readConnection(ResultSet resultSet, String prefix) throws SQLException {
        return new IntegrationConnectionDetails(
                resultSet.getObject(prefix + "id", UUID.class),
                resultSet.getObject(prefix + "tenant_id", UUID.class),
                resultSet.getObject(prefix + "org_id", UUID.class),
                resultSet.getString(prefix + "provider_key"),
                resultSet.getString(prefix + "integration_type"),
                resultSet.getString(prefix + "display_name"),
                resultSet.getString(prefix + "external_account_id"),
                resultSet.getString(prefix + "external_account_name"),
                resultSet.getString(prefix + "status"),
                codec.jsonObject(resultSet, prefix + "config_json"),
                resultSet.getObject(prefix + "created_by", UUID.class),
                nullableInstant(resultSet, prefix + "last_verified_at"),
                instant(resultSet, prefix + "created_at"),
                instant(resultSet, prefix + "updated_at"));
    }

    private IntegrationBindingDetails readBinding(ResultSet resultSet) throws SQLException {
        return readBinding(resultSet, "");
    }

    private IntegrationBindingDetails readBinding(ResultSet resultSet, String prefix) throws SQLException {
        return new IntegrationBindingDetails(
                resultSet.getObject(prefix + "id", UUID.class),
                resultSet.getObject(prefix + "tenant_id", UUID.class),
                resultSet.getObject(prefix + "connection_id", UUID.class),
                resultSet.getString(prefix + "scope_type"),
                resultSet.getObject(prefix + "scope_id", UUID.class),
                codec.textArray(resultSet, prefix + "capabilities"),
                codec.jsonObject(resultSet, prefix + "config_json"),
                resultSet.getString(prefix + "status"),
                instant(resultSet, prefix + "created_at"),
                instant(resultSet, prefix + "updated_at"));
    }

    private IntegrationCredentialDetails readCredential(ResultSet resultSet) throws SQLException {
        return new IntegrationCredentialDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("connection_id", UUID.class),
                resultSet.getString("credential_kind"),
                resultSet.getString("secret_ref"),
                resultSet.getInt("key_version"),
                resultSet.getString("status"),
                nullableInstant(resultSet, "expires_at"),
                nullableInstant(resultSet, "last_rotated_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private IntegrationWebhookEndpointDetails readWebhookEndpoint(ResultSet resultSet) throws SQLException {
        return new IntegrationWebhookEndpointDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("connection_id", UUID.class),
                resultSet.getString("provider_key"),
                resultSet.getString("external_webhook_id"),
                resultSet.getString("endpoint_url"),
                codec.textArray(resultSet, "event_types"),
                resultSet.getString("status"),
                resultSet.getString("signing_secret_ref"),
                codec.jsonObject(resultSet, "config_json"),
                codec.jsonObject(resultSet, "last_delivery_json"),
                nullableInstant(resultSet, "last_delivered_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private IntegrationSyncStateDetails readSyncState(ResultSet resultSet) throws SQLException {
        return new IntegrationSyncStateDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("connection_id", UUID.class),
                resultSet.getString("sync_type"),
                resultSet.getString("scope_type"),
                resultSet.getObject("scope_id", UUID.class),
                resultSet.getString("status"),
                codec.jsonObject(resultSet, "cursor_json"),
                codec.jsonObject(resultSet, "checkpoint_json"),
                codec.jsonObject(resultSet, "last_error_json"),
                nullableInstant(resultSet, "last_started_at"),
                nullableInstant(resultSet, "last_completed_at"),
                nullableInstant(resultSet, "next_run_at"),
                nullableInstant(resultSet, "lease_expires_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private IntegrationEventDetails readEvent(ResultSet resultSet) throws SQLException {
        return new IntegrationEventDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("connection_id", UUID.class),
                resultSet.getObject("webhook_endpoint_id", UUID.class),
                resultSet.getString("provider_key"),
                resultSet.getString("event_type"),
                resultSet.getString("external_event_id"),
                resultSet.getString("scope_type"),
                resultSet.getObject("scope_id", UUID.class),
                resultSet.getString("status"),
                codec.jsonObject(resultSet, "payload"),
                codec.jsonObject(resultSet, "error_json"),
                instant(resultSet, "received_at"),
                nullableInstant(resultSet, "processed_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        OffsetDateTime value = resultSet.getObject(columnName, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void setNullableInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setObject(index, value == null ? null : utc(value));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant nextDatabaseUpdatedAt(Instant requestedUpdatedAt, Instant expectedUpdatedAt) {
        if (expectedUpdatedAt == null) {
            return requestedUpdatedAt;
        }
        Instant requestedMicros = requestedUpdatedAt.truncatedTo(ChronoUnit.MICROS);
        Instant expectedMicros = expectedUpdatedAt.truncatedTo(ChronoUnit.MICROS);
        return requestedMicros.isAfter(expectedMicros) ? requestedMicros : expectedMicros.plusNanos(1_000);
    }

    private static RuntimeException mapIntegrityFailure(
            String message,
            SQLException exception,
            String conflictMessage) {
        if (isConstraintFailure(exception)) {
            return new IntegrationException("conflict", conflictMessage);
        }
        return databaseFailure(message, exception);
    }

    private static boolean isConstraintFailure(SQLException exception) {
        String sqlState = exception.getSQLState();
        return exception instanceof SQLIntegrityConstraintViolationException
                || "23505".equals(sqlState)
                || "23503".equals(sqlState)
                || "23514".equals(sqlState);
    }

    private static RuntimeException databaseFailure(String message, SQLException exception) {
        return new IllegalStateException(message, exception);
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database failure.
        }
    }
}
