package dev.vericov.integrations.adapter.jdbc;

import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcIntegrationScopeValidator implements IntegrationScopeValidator {
    private final DataSource dataSource;

    public JdbcIntegrationScopeValidator(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void requireScope(UUID tenantId, UUID orgId, String scopeType, UUID scopeId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
        switch (scopeType) {
            case "organization" -> requireOrganizationScope(tenantId, orgId, scopeId);
            case "repository" -> requireRepositoryScope(tenantId, orgId, scopeId);
            case "component" -> requireComponentScope(tenantId, orgId, scopeId);
            default -> throw new IntegrationException("validation_error", "scope_type is invalid");
        }
    }

    private void requireOrganizationScope(UUID tenantId, UUID orgId, UUID scopeId) {
        if (!orgId.equals(scopeId)) {
            throw scopeNotFound();
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select 1
                        from vericov.organizations
                        where tenant_id = ?
                          and id = ?
                          and status <> 'deleted'
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw scopeNotFound();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate integration organization scope", exception);
        }
    }

    private void requireRepositoryScope(UUID tenantId, UUID orgId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select 1
                        from vericov.repositories
                        where tenant_id = ?
                          and org_id = ?
                          and id = ?
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw scopeNotFound();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate integration repository scope", exception);
        }
    }

    private void requireComponentScope(UUID tenantId, UUID orgId, UUID componentId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select 1
                        from vericov.components
                        where tenant_id = ?
                          and org_id = ?
                          and id = ?
                          and status = 'active'
                        limit 1
                        """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orgId);
            statement.setObject(3, componentId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw scopeNotFound();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate integration component scope", exception);
        }
    }

    private static IntegrationException scopeNotFound() {
        return new IntegrationException("not_found", "Integration scope not found");
    }
}
