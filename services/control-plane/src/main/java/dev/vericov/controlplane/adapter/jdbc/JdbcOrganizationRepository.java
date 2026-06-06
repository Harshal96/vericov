package dev.vericov.controlplane.adapter.jdbc;

import dev.vericov.controlplane.application.MembershipDetails;
import dev.vericov.controlplane.application.OrganizationDetails;
import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.PolicyDefaultsDetails;
import dev.vericov.controlplane.application.CoverageBadgeCacheEntry;
import dev.vericov.controlplane.application.CoverageLineHitMapDetails;
import dev.vericov.controlplane.application.CoverageMetricDetails;
import dev.vericov.controlplane.application.CoverageReportSummary;
import dev.vericov.controlplane.application.CoverageFileSummaryDetails;
import dev.vericov.controlplane.application.CoverageGapFindingDetails;
import dev.vericov.controlplane.application.DiffCoverageFileDetails;
import dev.vericov.controlplane.application.DiffCoverageLineDetails;
import dev.vericov.controlplane.application.GateEvaluationDetails;
import dev.vericov.controlplane.application.PullRequestDiffCoverageDetails;
import dev.vericov.controlplane.application.RepositoryBadgeSettingsDetails;
import dev.vericov.controlplane.application.RepositoryApiKeyDetails;
import dev.vericov.controlplane.application.RepositoryConfigDetails;
import dev.vericov.controlplane.application.RepositoryComponentDetails;
import dev.vericov.controlplane.application.RepositoryDetails;
import dev.vericov.controlplane.application.RepositoryGateDetails;
import dev.vericov.controlplane.application.RepositoryOwnerRuleDetails;
import dev.vericov.controlplane.application.RepositoryPackageNodeDetails;
import dev.vericov.controlplane.application.RepositoryPolicyDetails;
import dev.vericov.controlplane.application.CoverageDebtDetails;
import dev.vericov.controlplane.application.CoverageDebtEventDetails;
import dev.vericov.controlplane.application.TestRunDetails;
import dev.vericov.controlplane.application.port.OrganizationRepository;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcOrganizationRepository implements OrganizationRepository {
    private final DataSource dataSource;

    public JdbcOrganizationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<OrganizationDetails> findById(UUID organizationId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, name, slug, plan, status, created_at, updated_at
                        from vericov.organizations
                        where id = ?
                        """)) {
            statement.setObject(1, organizationId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readOrganization(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find organization", exception);
        }
    }

    @Override
    public Optional<MembershipDetails> findMembership(UUID organizationId, UUID userId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, supabase_user_id, role, status, created_at, updated_at
                        from vericov.memberships
                        where org_id = ?
                          and supabase_user_id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, userId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readMembership(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find membership", exception);
        }
    }

    @Override
    public List<RepositoryDetails> listRepositories(UUID organizationId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider,
                            provider_repository_id,
                            full_name,
                            default_branch,
                            visibility,
                            privacy_mode,
                            status,
                            created_at,
                            updated_at
                        from vericov.repositories
                        where org_id = ?
                        order by full_name, id
                        """)) {
            statement.setObject(1, organizationId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryDetails> repositories = new ArrayList<>();
                while (resultSet.next()) {
                    repositories.add(readRepository(resultSet));
                }
                return List.copyOf(repositories);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repositories", exception);
        }
    }

    @Override
    public Optional<RepositoryDetails> findRepository(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider,
                            provider_repository_id,
                            full_name,
                            default_branch,
                            visibility,
                            privacy_mode,
                            status,
                            created_at,
                            updated_at
                        from vericov.repositories
                        where org_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepository(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository", exception);
        }
    }

    @Override
    public Optional<RepositoryDetails> findRepositoryById(UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider,
                            provider_repository_id,
                            full_name,
                            default_branch,
                            visibility,
                            privacy_mode,
                            status,
                            created_at,
                            updated_at
                        from vericov.repositories
                        where id = ?
                        """)) {
            statement.setObject(1, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepository(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository", exception);
        }
    }

    @Override
    public Optional<RepositoryDetails> findRepositoryByProviderIdentity(
            UUID organizationId,
            String provider,
            String providerRepositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            org_id,
                            provider,
                            provider_repository_id,
                            full_name,
                            default_branch,
                            visibility,
                            privacy_mode,
                            status,
                            created_at,
                            updated_at
                        from vericov.repositories
                        where org_id = ?
                          and provider = ?
                          and provider_repository_id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setString(2, provider);
            statement.setString(3, providerRepositoryId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepository(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository provider identity", exception);
        }
    }

    @Override
    public RepositoryDetails saveRepository(RepositoryDetails repository) {
        try (var connection = dataSource.getConnection()) {
            insertRepository(connection, repository);
            return repository;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository", exception);
        }
    }

    @Override
    public RepositoryDetails updateRepository(RepositoryDetails repository) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repositories
                        set full_name = ?,
                            default_branch = ?,
                            visibility = ?,
                            status = ?,
                            updated_at = ?
                        where id = ?
                          and org_id = ?
                        """)) {
            int index = 1;
            statement.setString(index++, repository.fullName());
            statement.setString(index++, repository.defaultBranch());
            statement.setString(index++, repository.visibility());
            statement.setString(index++, repository.status());
            statement.setObject(index++, utc(repository.updatedAt()));
            statement.setObject(index++, repository.id());
            statement.setObject(index, repository.organizationId());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new OrganizationException("not_found", "Repository not found");
            }
            return repository;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update repository", exception);
        }
    }

    @Override
    public List<RepositoryComponentDetails> listRepositoryComponents(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, description, path_patterns,
                               owners, criticality, metadata_json, status, created_by_user_id, created_at, updated_at
                        from vericov.components
                        where org_id = ?
                          and repository_id = ?
                        order by name, id
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryComponentDetails> components = new ArrayList<>();
                while (resultSet.next()) {
                    components.add(readRepositoryComponent(resultSet));
                }
                return List.copyOf(components);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository components", exception);
        }
    }

    @Override
    public Optional<RepositoryComponentDetails> findRepositoryComponent(
            UUID organizationId,
            UUID repositoryId,
            UUID componentId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, description, path_patterns,
                               owners, criticality, metadata_json, status, created_by_user_id, created_at, updated_at
                        from vericov.components
                        where org_id = ?
                          and repository_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            statement.setObject(3, componentId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepositoryComponent(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository component", exception);
        }
    }

    @Override
    public RepositoryComponentDetails saveRepositoryComponent(RepositoryComponentDetails component) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.components (
                            id, tenant_id, org_id, repository_id, name, description, path_patterns, owners,
                            criticality, metadata_json, status, created_by_user_id, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                        """)) {
            setRepositoryComponent(statement, connection, component);
            statement.executeUpdate();
            return component;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository component", exception);
        }
    }

    @Override
    public RepositoryComponentDetails updateRepositoryComponent(RepositoryComponentDetails component) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.components
                        set name = ?,
                            description = ?,
                            path_patterns = ?,
                            owners = ?,
                            criticality = ?,
                            metadata_json = ?::jsonb,
                            status = ?,
                            updated_at = ?
                        where org_id = ?
                          and repository_id = ?
                          and id = ?
                        """)) {
            int index = 1;
            statement.setString(index++, component.name());
            statement.setString(index++, component.description());
            statement.setArray(index++, connection.createArrayOf("text", component.pathPatterns().toArray(String[]::new)));
            statement.setArray(index++, connection.createArrayOf("text", component.owners().toArray(String[]::new)));
            statement.setString(index++, component.criticality());
            statement.setString(index++, jsonObject(component.metadata()));
            statement.setString(index++, component.status());
            statement.setObject(index++, utc(component.updatedAt()));
            statement.setObject(index++, component.organizationId());
            statement.setObject(index++, component.repositoryId());
            statement.setObject(index, component.id());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new OrganizationException("not_found", "Repository component not found");
            }
            return component;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to update repository component", exception);
        }
    }

    @Override
    public List<RepositoryOwnerRuleDetails> listRepositoryOwnerRules(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, source, pattern, owners, priority,
                               source_ref, status, created_at, updated_at
                        from vericov.repository_owner_rules
                        where org_id = ?
                          and repository_id = ?
                        order by priority, id
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryOwnerRuleDetails> rules = new ArrayList<>();
                while (resultSet.next()) {
                    rules.add(readRepositoryOwnerRule(resultSet));
                }
                return List.copyOf(rules);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository owner rules", exception);
        }
    }

    @Override
    public void replaceRepositoryOwnerRules(
            UUID organizationId,
            UUID repositoryId,
            List<RepositoryOwnerRuleDetails> ownerRules) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement("""
                    delete from vericov.repository_owner_rules
                    where org_id = ?
                      and repository_id = ?
                    """)) {
                delete.setObject(1, organizationId);
                delete.setObject(2, repositoryId);
                delete.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    insert into vericov.repository_owner_rules (
                        id, tenant_id, org_id, repository_id, source, pattern, owners, priority,
                        source_ref, status, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (RepositoryOwnerRuleDetails rule : ownerRules) {
                    int index = 1;
                    insert.setObject(index++, rule.id());
                    insert.setObject(index++, rule.tenantId());
                    insert.setObject(index++, rule.organizationId());
                    insert.setObject(index++, rule.repositoryId());
                    insert.setString(index++, rule.source());
                    insert.setString(index++, rule.pattern());
                    insert.setArray(index++, connection.createArrayOf("text", rule.owners().toArray(String[]::new)));
                    insert.setInt(index++, rule.priority());
                    insert.setString(index++, rule.sourceRef());
                    insert.setString(index++, rule.status());
                    insert.setObject(index++, utc(rule.createdAt()));
                    insert.setObject(index, utc(rule.updatedAt()));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw databaseFailure("Failed to replace repository owner rules", exception);
        }
    }

    @Override
    public List<RepositoryPackageNodeDetails> listRepositoryPackageNodes(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, component_id, package_name, package_path,
                               manifest_path, ecosystem, metadata_json, status, created_at, updated_at
                        from vericov.repository_package_nodes
                        where org_id = ?
                          and repository_id = ?
                        order by package_path, id
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryPackageNodeDetails> nodes = new ArrayList<>();
                while (resultSet.next()) {
                    nodes.add(readRepositoryPackageNode(resultSet));
                }
                return List.copyOf(nodes);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository package nodes", exception);
        }
    }

    @Override
    public void replaceRepositoryPackageNodes(
            UUID organizationId,
            UUID repositoryId,
            List<RepositoryPackageNodeDetails> packageNodes) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var delete = connection.prepareStatement("""
                    delete from vericov.repository_package_nodes
                    where org_id = ?
                      and repository_id = ?
                    """)) {
                delete.setObject(1, organizationId);
                delete.setObject(2, repositoryId);
                delete.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    insert into vericov.repository_package_nodes (
                        id, tenant_id, org_id, repository_id, component_id, package_name, package_path,
                        manifest_path, ecosystem, metadata_json, status, created_at, updated_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """)) {
                for (RepositoryPackageNodeDetails node : packageNodes) {
                    int index = 1;
                    insert.setObject(index++, node.id());
                    insert.setObject(index++, node.tenantId());
                    insert.setObject(index++, node.organizationId());
                    insert.setObject(index++, node.repositoryId());
                    insert.setObject(index++, node.componentId());
                    insert.setString(index++, node.packageName());
                    insert.setString(index++, node.packagePath());
                    insert.setString(index++, node.manifestPath());
                    insert.setString(index++, node.ecosystem());
                    insert.setString(index++, jsonObject(node.metadata()));
                    insert.setString(index++, node.status());
                    insert.setObject(index++, utc(node.createdAt()));
                    insert.setObject(index, utc(node.updatedAt()));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw databaseFailure("Failed to replace repository package nodes", exception);
        }
    }

    @Override
    public List<RepositoryApiKeyDetails> listRepositoryApiKeys(UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            repository_id,
                            name,
                            key_prefix,
                            key_hash,
                            scopes,
                            branch_allow_patterns,
                            expires_at,
                            revoked_at,
                            created_by_user_id,
                            revoked_by_user_id,
                            last_used_at,
                            created_at,
                            updated_at
                        from vericov.repository_api_keys
                        where repository_id = ?
                        order by created_at, id
                        """)) {
            statement.setObject(1, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryApiKeyDetails> apiKeys = new ArrayList<>();
                while (resultSet.next()) {
                    apiKeys.add(readRepositoryApiKey(resultSet));
                }
                return List.copyOf(apiKeys);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository API keys", exception);
        }
    }

    @Override
    public Optional<RepositoryApiKeyDetails> findRepositoryApiKey(UUID repositoryId, UUID apiKeyId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id,
                            tenant_id,
                            repository_id,
                            name,
                            key_prefix,
                            key_hash,
                            scopes,
                            branch_allow_patterns,
                            expires_at,
                            revoked_at,
                            created_by_user_id,
                            revoked_by_user_id,
                            last_used_at,
                            created_at,
                            updated_at
                        from vericov.repository_api_keys
                        where repository_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setObject(2, apiKeyId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepositoryApiKey(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository API key", exception);
        }
    }

    @Override
    public RepositoryApiKeyDetails saveRepositoryApiKey(RepositoryApiKeyDetails apiKey) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.repository_api_keys (
                            id,
                            tenant_id,
                            repository_id,
                            name,
                            key_prefix,
                            key_hash,
                            scopes,
                            branch_allow_patterns,
                            expires_at,
                            revoked_at,
                            created_by_user_id,
                            revoked_by_user_id,
                            last_used_at,
                            created_at,
                            updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            setRepositoryApiKey(statement, apiKey);
            statement.executeUpdate();
            return apiKey;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository API key", exception);
        }
    }

    @Override
    public RepositoryApiKeyDetails updateRepositoryApiKey(RepositoryApiKeyDetails apiKey) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repository_api_keys
                        set name = ?,
                            scopes = ?,
                            branch_allow_patterns = ?,
                            expires_at = ?,
                            revoked_at = ?,
                            revoked_by_user_id = ?,
                            last_used_at = ?,
                            updated_at = ?
                        where repository_id = ?
                          and id = ?
                        """)) {
            statement.setString(1, apiKey.name());
            statement.setArray(2, connection.createArrayOf("text", apiKey.scopes().toArray(String[]::new)));
            statement.setArray(3, connection.createArrayOf("text", apiKey.branchAllowPatterns().toArray(String[]::new)));
            setNullableInstant(statement, 4, apiKey.expiresAt());
            setNullableInstant(statement, 5, apiKey.revokedAt());
            statement.setObject(6, apiKey.revokedByUserId());
            setNullableInstant(statement, 7, apiKey.lastUsedAt());
            statement.setObject(8, utc(apiKey.updatedAt()));
            statement.setObject(9, apiKey.repositoryId());
            statement.setObject(10, apiKey.id());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new OrganizationException("not_found", "Repository API key not found");
            }
            return apiKey;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update repository API key", exception);
        }
    }

    @Override
    public Optional<PolicyDefaultsDetails> findPolicyDefaults(UUID organizationId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, defaults_json, schema_version, updated_by_user_id, created_at, updated_at
                        from vericov.organization_policy_defaults
                        where org_id = ?
                        """)) {
            statement.setObject(1, organizationId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readPolicyDefaults(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find policy defaults", exception);
        }
    }

    @Override
    public PolicyDefaultsDetails savePolicyDefaults(PolicyDefaultsDetails defaults) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.organization_policy_defaults (
                            id, tenant_id, org_id, defaults_json, schema_version, updated_by_user_id, created_at, updated_at
                        )
                        values (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                        """)) {
            int index = 1;
            statement.setObject(index++, defaults.id());
            statement.setObject(index++, defaults.tenantId());
            statement.setObject(index++, defaults.organizationId());
            statement.setString(index++, jsonObject(defaults.defaults()));
            statement.setInt(index++, defaults.schemaVersion());
            statement.setObject(index++, defaults.updatedByUserId());
            statement.setObject(index++, utc(defaults.createdAt()));
            statement.setObject(index, utc(defaults.updatedAt()));
            statement.executeUpdate();
            return defaults;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save policy defaults", exception);
        }
    }

    @Override
    public PolicyDefaultsDetails updatePolicyDefaults(PolicyDefaultsDetails defaults) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.organization_policy_defaults
                        set defaults_json = ?::jsonb,
                            schema_version = ?,
                            updated_by_user_id = ?,
                            updated_at = ?
                        where org_id = ?
                        """)) {
            int index = 1;
            statement.setString(index++, jsonObject(defaults.defaults()));
            statement.setInt(index++, defaults.schemaVersion());
            statement.setObject(index++, defaults.updatedByUserId());
            statement.setObject(index++, utc(defaults.updatedAt()));
            statement.setObject(index, defaults.organizationId());
            if (statement.executeUpdate() == 0) {
                throw new OrganizationException("not_found", "Policy defaults not found");
            }
            return defaults;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update policy defaults", exception);
        }
    }

    @Override
    public Optional<RepositoryConfigDetails> findRepositoryConfig(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, source, config_json, schema_version,
                               validation_status, validation_errors, updated_by_user_id, created_at, updated_at
                        from vericov.repository_configs
                        where org_id = ?
                          and repository_id = ?
                          and source = 'ui_override'
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepositoryConfig(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository config", exception);
        }
    }

    @Override
    public RepositoryConfigDetails saveRepositoryConfig(RepositoryConfigDetails config) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.repository_configs (
                            id, tenant_id, org_id, repository_id, source, config_json, schema_version,
                            validation_status, validation_errors, updated_by_user_id, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?)
                        """)) {
            bindRepositoryConfig(statement, config);
            statement.executeUpdate();
            return config;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository config", exception);
        }
    }

    @Override
    public RepositoryConfigDetails updateRepositoryConfig(RepositoryConfigDetails config) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repository_configs
                        set config_json = ?::jsonb,
                            schema_version = ?,
                            validation_status = ?,
                            validation_errors = ?::jsonb,
                            updated_by_user_id = ?,
                            updated_at = ?
                        where org_id = ?
                          and repository_id = ?
                          and source = ?
                        """)) {
            int index = 1;
            statement.setString(index++, jsonObject(config.config()));
            statement.setInt(index++, config.schemaVersion());
            statement.setString(index++, config.validationStatus());
            statement.setString(index++, jsonStringArray(config.validationErrors()));
            statement.setObject(index++, config.updatedByUserId());
            statement.setObject(index++, utc(config.updatedAt()));
            statement.setObject(index++, config.organizationId());
            statement.setObject(index++, config.repositoryId());
            statement.setString(index, config.source());
            if (statement.executeUpdate() == 0) {
                throw new OrganizationException("not_found", "Repository config not found");
            }
            return config;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update repository config", exception);
        }
    }

    @Override
    public List<RepositoryPolicyDetails> listRepositoryPolicies(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, description, policy_type, target_type,
                               target_selector, config_json, status, priority, created_by_user_id, created_at, updated_at
                        from vericov.repository_policies
                        where org_id = ?
                          and repository_id = ?
                        order by priority, name, id
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryPolicyDetails> policies = new ArrayList<>();
                while (resultSet.next()) {
                    policies.add(readRepositoryPolicy(resultSet));
                }
                return List.copyOf(policies);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository policies", exception);
        }
    }

    @Override
    public Optional<RepositoryPolicyDetails> findRepositoryPolicy(UUID organizationId, UUID repositoryId, UUID policyId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, description, policy_type, target_type,
                               target_selector, config_json, status, priority, created_by_user_id, created_at, updated_at
                        from vericov.repository_policies
                        where org_id = ?
                          and repository_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            statement.setObject(3, policyId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepositoryPolicy(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository policy", exception);
        }
    }

    @Override
    public RepositoryPolicyDetails saveRepositoryPolicy(RepositoryPolicyDetails policy) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.repository_policies (
                            id, tenant_id, org_id, repository_id, name, description, policy_type, target_type,
                            target_selector, config_json, status, priority, created_by_user_id, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                        """)) {
            bindRepositoryPolicy(statement, policy);
            statement.executeUpdate();
            return policy;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository policy", exception);
        }
    }

    @Override
    public RepositoryPolicyDetails updateRepositoryPolicy(RepositoryPolicyDetails policy) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repository_policies
                        set name = ?,
                            description = ?,
                            policy_type = ?,
                            target_type = ?,
                            target_selector = ?,
                            config_json = ?::jsonb,
                            status = ?,
                            priority = ?,
                            updated_at = ?
                        where org_id = ?
                          and repository_id = ?
                          and id = ?
                        """)) {
            int index = 1;
            statement.setString(index++, policy.name());
            statement.setString(index++, policy.description());
            statement.setString(index++, policy.policyType());
            statement.setString(index++, policy.targetType());
            statement.setString(index++, policy.targetSelector());
            statement.setString(index++, jsonObject(policy.config()));
            statement.setString(index++, policy.status());
            statement.setInt(index++, policy.priority());
            statement.setObject(index++, utc(policy.updatedAt()));
            statement.setObject(index++, policy.organizationId());
            statement.setObject(index++, policy.repositoryId());
            statement.setObject(index, policy.id());
            if (statement.executeUpdate() == 0) {
                throw new OrganizationException("not_found", "Repository policy not found");
            }
            return policy;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update repository policy", exception);
        }
    }

    @Override
    public List<RepositoryGateDetails> listRepositoryGates(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, name, gate_type, metric, threshold, max_drop,
                               blocking, config_json, status, created_at, updated_at
                        from vericov.repository_gate_configurations
                        where org_id = ?
                          and repository_id = ?
                        order by name, id
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                List<RepositoryGateDetails> gates = new ArrayList<>();
                while (resultSet.next()) {
                    gates.add(readRepositoryGate(resultSet));
                }
                return List.copyOf(gates);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list repository gates", exception);
        }
    }

    @Override
    public void replaceRepositoryGates(UUID organizationId, UUID repositoryId, List<RepositoryGateDetails> gates) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var delete = connection.prepareStatement("""
                        delete from vericov.repository_gate_configurations
                        where org_id = ?
                          and repository_id = ?
                        """)) {
                    delete.setObject(1, organizationId);
                    delete.setObject(2, repositoryId);
                    delete.executeUpdate();
                }
                for (RepositoryGateDetails gate : gates) {
                    insertRepositoryGate(connection, gate);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            }
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to replace repository gates", exception);
        }
    }

    @Override
    public Optional<RepositoryBadgeSettingsDetails> findRepositoryBadgeSettings(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, enabled, branch, metric, label,
                               thresholds_json, token_hash, token_prefix, created_by_user_id,
                               created_at, updated_at, revoked_at
                        from vericov.repository_badge_settings
                        where org_id = ?
                          and repository_id = ?
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readRepositoryBadgeSettings(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find repository badge settings", exception);
        }
    }

    @Override
    public RepositoryBadgeSettingsDetails saveRepositoryBadgeSettings(RepositoryBadgeSettingsDetails settings) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.repository_badge_settings (
                            id, tenant_id, org_id, repository_id, enabled, branch, metric, label,
                            thresholds_json, token_hash, token_prefix, created_by_user_id,
                            created_at, updated_at, revoked_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                        """)) {
            bindRepositoryBadgeSettings(statement, settings);
            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save repository badge settings", exception);
        }
    }

    @Override
    public RepositoryBadgeSettingsDetails updateRepositoryBadgeSettings(RepositoryBadgeSettingsDetails settings) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.repository_badge_settings
                        set enabled = ?,
                            branch = ?,
                            metric = ?,
                            label = ?,
                            thresholds_json = ?::jsonb,
                            token_hash = ?,
                            token_prefix = ?,
                            updated_at = ?,
                            revoked_at = ?
                        where org_id = ?
                          and repository_id = ?
                        """)) {
            int index = 1;
            statement.setBoolean(index++, settings.enabled());
            statement.setString(index++, settings.branch());
            statement.setString(index++, settings.metric());
            statement.setString(index++, settings.label());
            statement.setString(index++, jsonObject(settings.thresholds()));
            statement.setString(index++, settings.tokenHash());
            statement.setString(index++, settings.tokenPrefix());
            statement.setObject(index++, utc(settings.updatedAt()));
            setNullableInstant(statement, index++, settings.revokedAt());
            statement.setObject(index++, settings.organizationId());
            statement.setObject(index, settings.repositoryId());
            if (statement.executeUpdate() == 0) {
                throw new OrganizationException("not_found", "Repository badge settings not found");
            }
            return settings;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update repository badge settings", exception);
        }
    }

    @Override
    public Optional<CoverageBadgeCacheEntry> findFreshCoverageBadgeCache(
            UUID organizationId,
            UUID repositoryId,
            String cacheScope,
            String branch,
            String metric,
            Instant settingsUpdatedAt,
            Instant now) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, cache_scope, branch, metric,
                               label, message, color, commit_sha, coverage_percent, source_report_id,
                               report_created_at, settings_updated_at, cached_at, expires_at
                        from vericov.badge_cache
                        where org_id = ?
                          and repository_id = ?
                          and badge_type = 'coverage'
                          and cache_scope = ?
                          and branch = ?
                          and metric = ?
                          and settings_updated_at = ?
                          and expires_at > ?
                        """)) {
            int index = 1;
            statement.setObject(index++, organizationId);
            statement.setObject(index++, repositoryId);
            statement.setString(index++, cacheScope);
            statement.setString(index++, branch);
            statement.setString(index++, metric);
            statement.setObject(index++, utc(settingsUpdatedAt));
            statement.setObject(index, utc(now));
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageBadgeCacheEntry(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find coverage badge cache", exception);
        }
    }

    @Override
    public CoverageBadgeCacheEntry upsertCoverageBadgeCache(CoverageBadgeCacheEntry entry) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.badge_cache (
                            id, tenant_id, org_id, repository_id, badge_type, cache_scope, branch, metric,
                            label, message, color, commit_sha, coverage_percent, source_report_id,
                            report_created_at, settings_updated_at, cached_at, expires_at
                        )
                        values (?, ?, ?, ?, 'coverage', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (org_id, repository_id, badge_type, cache_scope, branch, metric) do update set
                            label = excluded.label,
                            message = excluded.message,
                            color = excluded.color,
                            commit_sha = excluded.commit_sha,
                            coverage_percent = excluded.coverage_percent,
                            source_report_id = excluded.source_report_id,
                            report_created_at = excluded.report_created_at,
                            settings_updated_at = excluded.settings_updated_at,
                            cached_at = excluded.cached_at,
                            expires_at = excluded.expires_at
                        """)) {
            bindCoverageBadgeCache(statement, entry);
            statement.executeUpdate();
            return entry;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to save coverage badge cache", exception);
        }
    }

    @Override
    public void deleteCoverageBadgeCache(UUID organizationId, UUID repositoryId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        delete from vericov.badge_cache
                        where org_id = ?
                          and repository_id = ?
                          and badge_type = 'coverage'
                        """)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw databaseFailure("Failed to delete coverage badge cache", exception);
        }
    }

    @Override
    public Optional<CoverageReportSummary> findLatestCoverageReport(UUID repositoryId, String branch) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, upload_id, commit_sha, branch, pull_request_number,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               created_at, updated_at
                        from vericov.coverage_reports
                        where repository_id = ?
                          and branch = ?
                          and status = 'complete'
                        order by created_at desc, id desc
                        limit 1
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, branch);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageReportSummary(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find latest coverage report", exception);
        }
    }

    @Override
    public Optional<CoverageReportSummary> findCoverageReportByCommit(UUID repositoryId, String commitSha) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, upload_id, commit_sha, branch, pull_request_number,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               created_at, updated_at
                        from vericov.coverage_reports
                        where repository_id = ?
                          and commit_sha = ?
                          and status = 'complete'
                        order by created_at desc, id desc
                        limit 1
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, commitSha);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageReportSummary(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find coverage report by commit", exception);
        }
    }

    @Override
    public Optional<CoverageReportSummary> findLatestPullRequestCoverageReport(
            UUID repositoryId,
            int pullRequestNumber) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, upload_id, commit_sha, branch, pull_request_number,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               created_at, updated_at
                        from vericov.coverage_reports
                        where repository_id = ?
                          and pull_request_number = ?
                          and status = 'complete'
                        order by created_at desc, id desc
                        limit 1
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setInt(2, pullRequestNumber);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageReportSummary(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find pull request coverage report", exception);
        }
    }

    @Override
    public List<CoverageReportSummary> listCoverageReports(
            UUID repositoryId,
            String branch,
            Instant from,
            Instant to,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, tenant_id, repository_id, upload_id, commit_sha, branch, pull_request_number,
                       line_covered, line_total, branch_covered, branch_total,
                       function_covered, function_total, statement_covered, statement_total,
                       created_at, updated_at
                from vericov.coverage_reports
                where repository_id = ?
                  and branch = ?
                  and status = 'complete'
                """);
        List<Object> params = new ArrayList<>(List.of(repositoryId, branch));
        if (from != null) {
            sql.append(" and created_at >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" and created_at <= ?");
            params.add(to);
        }
        sql.append(" order by created_at, id limit ?");
        params.add(limit);

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Instant instant) {
                    statement.setObject(i + 1, utc(instant));
                } else {
                    statement.setObject(i + 1, param);
                }
            }
            try (var resultSet = statement.executeQuery()) {
                List<CoverageReportSummary> reports = new ArrayList<>();
                while (resultSet.next()) {
                    reports.add(readCoverageReportSummary(resultSet));
                }
                return List.copyOf(reports);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list coverage reports", exception);
        }
    }

    @Override
    public List<CoverageFileSummaryDetails> listCoverageFileSummaries(UUID coverageReportId, int limit) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, coverage_report_id, repository_id, commit_sha, file_path,
                               line_covered, line_total, branch_covered, branch_total,
                               function_covered, function_total, statement_covered, statement_total,
                               created_at
                        from vericov.coverage_file_summaries
                        where coverage_report_id = ?
                        order by file_path, id
                        limit ?
                        """)) {
            statement.setObject(1, coverageReportId);
            statement.setInt(2, limit);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageFileSummaryDetails> summaries = new ArrayList<>();
                while (resultSet.next()) {
                    summaries.add(readCoverageFileSummary(resultSet));
                }
                return List.copyOf(summaries);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list coverage file summaries", exception);
        }
    }

    @Override
    public Optional<PullRequestDiffCoverageDetails> findPullRequestDiffCoverage(
            UUID coverageReportId,
            boolean includeLines) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, coverage_report_id, base_sha, head_sha, status,
                               patch_line_covered, patch_line_total,
                               newly_missed_line_count, lost_coverage_line_count,
                               created_at, updated_at
                        from vericov.pull_request_coverage_diffs
                        where coverage_report_id = ?
                        """)) {
            statement.setObject(1, coverageReportId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                UUID diffId = resultSet.getObject("id", UUID.class);
                List<DiffCoverageFileDetails> files = readDiffCoverageFiles(connection, diffId, includeLines);
                return Optional.of(new PullRequestDiffCoverageDetails(
                        diffId,
                        resultSet.getObject("coverage_report_id", UUID.class),
                        resultSet.getString("base_sha"),
                        resultSet.getString("head_sha"),
                        resultSet.getString("status"),
                        CoverageMetricDetails.of(
                                resultSet.getInt("patch_line_covered"),
                                resultSet.getInt("patch_line_total")),
                        resultSet.getInt("newly_missed_line_count"),
                        resultSet.getInt("lost_coverage_line_count"),
                        files,
                        instant(resultSet, "created_at"),
                        instant(resultSet, "updated_at")));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find pull request diff coverage", exception);
        }
    }

    @Override
    public CoverageLineHitMapDetails findCoverageLineHits(
            UUID repositoryId,
            String commitSha,
            String filePath) {
        try (var connection = dataSource.getConnection()) {
            UUID coverageReportId = findCoverageReportId(connection, repositoryId, commitSha);
            if (coverageReportId == null) {
                return new CoverageLineHitMapDetails(repositoryId, null, commitSha, Map.of(filePath, Map.of()));
            }
            Map<Integer, Long> lineHits = readCoverageLineHits(connection, coverageReportId, filePath);
            return new CoverageLineHitMapDetails(
                    repositoryId,
                    coverageReportId,
                    commitSha,
                    Map.of(filePath, lineHits));
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find coverage line hits", exception);
        }
    }

    @Override
    public List<GateEvaluationDetails> listGateEvaluations(
            UUID organizationId,
            UUID repositoryId,
            String branch,
            String status,
            int limit) {
        String sql = """
                select id, tenant_id, org_id, repository_id, coverage_report_id, commit_sha, branch,
                       pull_request_number, gate_name, gate_type, metric, threshold, actual,
                       status, blocking, details_json, evaluated_at
                from vericov.gate_evaluations
                where org_id = ?
                  and repository_id = ?
                  and (? is null or branch = ?)
                  and (? is null or status = ?)
                order by evaluated_at desc, id desc
                limit ?
                """;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, repositoryId);
            statement.setString(3, branch);
            statement.setString(4, branch);
            statement.setString(5, status);
            statement.setString(6, status);
            statement.setInt(7, limit);
            try (var resultSet = statement.executeQuery()) {
                List<GateEvaluationDetails> evaluations = new ArrayList<>();
                while (resultSet.next()) {
                    evaluations.add(readGateEvaluation(resultSet));
                }
                return List.copyOf(evaluations);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list gate evaluations", exception);
        }
    }

    @Override
    public Optional<CoverageGapFindingDetails> findCoverageGap(UUID repositoryId, UUID gapId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, coverage_report_id, pr_diff_id,
                               component_id, commit_sha, pull_request_number, file_path, target_type,
                               line_start, line_end, symbol_name, reason_code, explanation,
                               confidence, risk_score, risk_level, owners, next_action, status,
                               evidence_json, created_at, updated_at
                        from vericov.coverage_gap_findings
                        where repository_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setObject(2, gapId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageGap(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find coverage gap", exception);
        }
    }

    @Override
    public List<CoverageGapFindingDetails> listCoverageGaps(
            UUID organizationId,
            UUID repositoryId,
            String commitSha,
            Integer pullRequestNumber,
            UUID componentId,
            String owner,
            String minRisk,
            String riskLevel,
            String status,
            String reasonCode,
            boolean includeDebt,
            int limit) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, coverage_report_id, pr_diff_id,
                               component_id, commit_sha, pull_request_number, file_path, target_type,
                               line_start, line_end, symbol_name, reason_code, explanation,
                               confidence, risk_score, risk_level, owners, next_action, status,
                               evidence_json, created_at, updated_at
                        from vericov.coverage_gap_findings
                        where org_id = ?
                          and repository_id = ?
                          and (? is null or commit_sha = ?)
                          and (? is null or pull_request_number = ?)
                          and (? is null or component_id = ?)
                          and (? is null or ? = any(owners))
                          and (? is null or risk_level = ?)
                          and (? is null or status = ?)
                          and (? is null or reason_code = ?)
                          and (? or status <> 'debt_suppressed')
                          and (? is null or
                              case risk_level
                                  when 'critical' then 4
                                  when 'high' then 3
                                  when 'medium' then 2
                                  else 1
                              end >=
                              case ?
                                  when 'critical' then 4
                                  when 'high' then 3
                                  when 'medium' then 2
                                  else 1
                              end)
                        order by
                          case when status = 'debt_suppressed' then 1 else 0 end,
                          risk_score desc,
                          case when reason_code in ('new_uncovered_changed_line', 'lost_existing_coverage', 'expired_debt_reappeared') then 0 else 1 end,
                          created_at,
                          file_path,
                          line_start nulls last,
                          id
                        limit ?
                        """)) {
            int index = 1;
            statement.setObject(index++, organizationId);
            statement.setObject(index++, repositoryId);
            statement.setString(index++, commitSha);
            statement.setString(index++, commitSha);
            if (pullRequestNumber == null) {
                statement.setNull(index++, java.sql.Types.INTEGER);
                statement.setNull(index++, java.sql.Types.INTEGER);
            } else {
                statement.setInt(index++, pullRequestNumber);
                statement.setInt(index++, pullRequestNumber);
            }
            statement.setObject(index++, componentId);
            statement.setObject(index++, componentId);
            statement.setString(index++, owner);
            statement.setString(index++, owner);
            statement.setString(index++, riskLevel);
            statement.setString(index++, riskLevel);
            statement.setString(index++, status);
            statement.setString(index++, status);
            statement.setString(index++, reasonCode);
            statement.setString(index++, reasonCode);
            statement.setBoolean(index++, includeDebt);
            statement.setString(index++, minRisk);
            statement.setString(index++, minRisk);
            statement.setInt(index, limit);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageGapFindingDetails> gaps = new ArrayList<>();
                while (resultSet.next()) {
                    gaps.add(readCoverageGap(resultSet));
                }
                return List.copyOf(gaps);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list coverage gaps", exception);
        }
    }

    @Override
    public Optional<CoverageDebtDetails> findCoverageDebt(UUID repositoryId, UUID debtId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select
                            id, tenant_id, org_id, repository_id, component_id, source_gap_id, source_report_id,
                            source_commit_sha, pull_request_number, target_type, file_path, line_start, line_end,
                            symbol_name, risk_level, reason, owner, status, expires_at, resolved_at,
                            resolved_by_user_id, revoked_at, revoked_by_user_id, linked_issue_url,
                            metadata_json, created_by_user_id, created_at, updated_at
                        from vericov.coverage_debt_items
                        where repository_id = ?
                          and id = ?
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setObject(2, debtId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCoverageDebt(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to find coverage debt item", exception);
        }
    }

    @Override
    public List<CoverageDebtDetails> listCoverageDebts(
            UUID repositoryId,
            String status,
            String owner,
            String riskLevel,
            UUID componentId,
            Instant expiresBefore,
            boolean includeExpired,
            UUID sourceGapId,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                select
                    id, tenant_id, org_id, repository_id, component_id, source_gap_id, source_report_id,
                    source_commit_sha, pull_request_number, target_type, file_path, line_start, line_end,
                    symbol_name, risk_level, reason, owner, status, expires_at, resolved_at,
                    resolved_by_user_id, revoked_at, revoked_by_user_id, linked_issue_url,
                    metadata_json, created_by_user_id, created_at, updated_at
                from vericov.coverage_debt_items
                where repository_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(repositoryId);

        Instant now = Instant.now();

        if (status != null) {
            if ("expired".equals(status)) {
                sql.append(" and (status = 'expired' or (status = 'active' and expires_at <= ?))");
                params.add(now);
            } else if ("active".equals(status)) {
                sql.append(" and status = 'active' and expires_at > ?");
                params.add(now);
            } else {
                sql.append(" and status = ?");
                params.add(status);
            }
        } else if (!includeExpired) {
            sql.append(" and status <> 'expired' and (status <> 'active' or expires_at > ?)");
            params.add(now);
        }

        if (owner != null) {
            sql.append(" and owner = ?");
            params.add(owner);
        }
        if (riskLevel != null) {
            sql.append(" and risk_level = ?");
            params.add(riskLevel);
        }
        if (componentId != null) {
            sql.append(" and component_id = ?");
            params.add(componentId);
        }
        if (expiresBefore != null) {
            sql.append(" and expires_at < ?");
            params.add(expiresBefore);
        }
        if (sourceGapId != null) {
            sql.append(" and source_gap_id = ?");
            params.add(sourceGapId);
        }

        sql.append(" order by created_at desc, id desc limit ?");
        params.add(limit);

        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Instant inst) {
                    statement.setObject(i + 1, utc(inst));
                } else {
                    statement.setObject(i + 1, p);
                }
            }
            try (var resultSet = statement.executeQuery()) {
                List<CoverageDebtDetails> list = new ArrayList<>();
                while (resultSet.next()) {
                    list.add(readCoverageDebt(resultSet));
                }
                return List.copyOf(list);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list coverage debts", exception);
        }
    }

    @Override
    public CoverageDebtDetails saveCoverageDebt(CoverageDebtDetails debt) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.coverage_debt_items (
                            id, tenant_id, org_id, repository_id, component_id, source_gap_id, source_report_id,
                            source_commit_sha, pull_request_number, target_type, file_path, line_start, line_end,
                            symbol_name, risk_level, reason, owner, status, expires_at, resolved_at,
                            resolved_by_user_id, revoked_at, revoked_by_user_id, linked_issue_url,
                            metadata_json, created_by_user_id, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                        """)) {
            int index = 1;
            statement.setObject(index++, debt.id());
            statement.setObject(index++, debt.tenantId());
            statement.setObject(index++, debt.organizationId());
            statement.setObject(index++, debt.repositoryId());
            statement.setObject(index++, debt.componentId());
            statement.setObject(index++, debt.sourceGapId());
            statement.setObject(index++, debt.sourceReportId());
            statement.setString(index++, debt.sourceCommitSha());
            statement.setObject(index++, debt.pullRequestNumber());
            statement.setString(index++, debt.targetType());
            statement.setString(index++, debt.filePath());
            statement.setObject(index++, debt.lineStart());
            statement.setObject(index++, debt.lineEnd());
            statement.setString(index++, debt.symbolName());
            statement.setString(index++, debt.riskLevel());
            statement.setString(index++, debt.reason());
            statement.setString(index++, debt.owner());
            statement.setString(index++, debt.status());
            setNullableInstant(statement, index++, debt.expiresAt());
            setNullableInstant(statement, index++, debt.resolvedAt());
            statement.setObject(index++, debt.resolvedByUserId());
            setNullableInstant(statement, index++, debt.revokedAt());
            statement.setObject(index++, debt.revokedByUserId());
            statement.setString(index++, debt.linkedIssueUrl());
            statement.setString(index++, jsonObject(debt.metadata()));
            statement.setObject(index++, debt.createdByUserId());
            statement.setObject(index++, utc(debt.createdAt()));
            statement.setObject(index, utc(debt.updatedAt()));
            statement.executeUpdate();
            return debt;
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save coverage debt item", exception);
        }
    }

    @Override
    public CoverageDebtDetails updateCoverageDebt(CoverageDebtDetails debt) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        update vericov.coverage_debt_items
                        set component_id = ?,
                            source_gap_id = ?,
                            source_report_id = ?,
                            source_commit_sha = ?,
                            pull_request_number = ?,
                            target_type = ?,
                            file_path = ?,
                            line_start = ?,
                            line_end = ?,
                            symbol_name = ?,
                            risk_level = ?,
                            reason = ?,
                            owner = ?,
                            status = ?,
                            expires_at = ?,
                            resolved_at = ?,
                            resolved_by_user_id = ?,
                            revoked_at = ?,
                            revoked_by_user_id = ?,
                            linked_issue_url = ?,
                            metadata_json = ?::jsonb,
                            updated_at = ?
                        where id = ?
                          and repository_id = ?
                        """)) {
            int index = 1;
            statement.setObject(index++, debt.componentId());
            statement.setObject(index++, debt.sourceGapId());
            statement.setObject(index++, debt.sourceReportId());
            statement.setString(index++, debt.sourceCommitSha());
            statement.setObject(index++, debt.pullRequestNumber());
            statement.setString(index++, debt.targetType());
            statement.setString(index++, debt.filePath());
            statement.setObject(index++, debt.lineStart());
            statement.setObject(index++, debt.lineEnd());
            statement.setString(index++, debt.symbolName());
            statement.setString(index++, debt.riskLevel());
            statement.setString(index++, debt.reason());
            statement.setString(index++, debt.owner());
            statement.setString(index++, debt.status());
            setNullableInstant(statement, index++, debt.expiresAt());
            setNullableInstant(statement, index++, debt.resolvedAt());
            statement.setObject(index++, debt.resolvedByUserId());
            setNullableInstant(statement, index++, debt.revokedAt());
            statement.setObject(index++, debt.revokedByUserId());
            statement.setString(index++, debt.linkedIssueUrl());
            statement.setString(index++, jsonObject(debt.metadata()));
            statement.setObject(index++, utc(debt.updatedAt()));
            statement.setObject(index++, debt.id());
            statement.setObject(index, debt.repositoryId());
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new OrganizationException("not_found", "Coverage debt item not found");
            }
            return debt;
        } catch (SQLException exception) {
            throw databaseFailure("Failed to update coverage debt item", exception);
        }
    }

    @Override
    public void saveCoverageDebtEvent(CoverageDebtEventDetails event) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        insert into vericov.coverage_debt_events (
                            id, tenant_id, org_id, repository_id, debt_item_id, event_type, actor_user_id, payload_json, created_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """)) {
            int index = 1;
            statement.setObject(index++, event.id());
            statement.setObject(index++, event.tenantId());
            statement.setObject(index++, event.organizationId());
            statement.setObject(index++, event.repositoryId());
            statement.setObject(index++, event.debtItemId());
            statement.setString(index++, event.eventType());
            statement.setObject(index++, event.actorUserId());
            statement.setString(index++, jsonObject(event.payload()));
            statement.setObject(index, utc(event.createdAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw mapIntegrityFailure("Failed to save coverage debt event", exception);
        }
    }

    @Override
    public List<CoverageDebtEventDetails> listCoverageDebtEvents(UUID debtItemId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, org_id, repository_id, debt_item_id, event_type, actor_user_id, payload_json, created_at
                        from vericov.coverage_debt_events
                        where debt_item_id = ?
                        order by created_at, id
                        """)) {
            statement.setObject(1, debtItemId);
            try (var resultSet = statement.executeQuery()) {
                List<CoverageDebtEventDetails> list = new ArrayList<>();
                while (resultSet.next()) {
                    list.add(new CoverageDebtEventDetails(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class),
                            resultSet.getObject("org_id", UUID.class),
                            resultSet.getObject("repository_id", UUID.class),
                            resultSet.getObject("debt_item_id", UUID.class),
                            resultSet.getString("event_type"),
                            resultSet.getObject("actor_user_id", UUID.class),
                            jsonMap(resultSet, "payload_json"),
                            instant(resultSet, "created_at")));
                }
                return List.copyOf(list);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list coverage debt events", exception);
        }
    }

    private static CoverageDebtDetails readCoverageDebt(ResultSet resultSet) throws SQLException {
        return new CoverageDebtDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("component_id", UUID.class),
                resultSet.getObject("source_gap_id", UUID.class),
                resultSet.getObject("source_report_id", UUID.class),
                resultSet.getString("source_commit_sha"),
                resultSet.getObject("pull_request_number") == null ? null : resultSet.getInt("pull_request_number"),
                resultSet.getString("target_type"),
                resultSet.getString("file_path"),
                resultSet.getObject("line_start") == null ? null : resultSet.getInt("line_start"),
                resultSet.getObject("line_end") == null ? null : resultSet.getInt("line_end"),
                resultSet.getString("symbol_name"),
                resultSet.getString("risk_level"),
                resultSet.getString("reason"),
                resultSet.getString("owner"),
                resultSet.getString("status"),
                instant(resultSet, "expires_at"),
                instant(resultSet, "resolved_at"),
                resultSet.getObject("resolved_by_user_id", UUID.class),
                instant(resultSet, "revoked_at"),
                resultSet.getObject("revoked_by_user_id", UUID.class),
                resultSet.getString("linked_issue_url"),
                jsonMap(resultSet, "metadata_json"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }


    @Override
    public List<TestRunDetails> listTestRuns(UUID repositoryId, String commitSha, int limit) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, tenant_id, repository_id, upload_id, upload_artifact_id,
                               commit_sha, branch, pull_request_number, suite_name, suite_index,
                               status, total_count, passed_count, failed_count, error_count,
                               skipped_count, duration_ms, created_at
                        from vericov.test_runs
                        where repository_id = ?
                          and commit_sha = ?
                        order by created_at desc, suite_index, id
                        limit ?
                        """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, commitSha);
            statement.setInt(3, limit);
            try (var resultSet = statement.executeQuery()) {
                List<TestRunDetails> runs = new ArrayList<>();
                while (resultSet.next()) {
                    runs.add(readTestRun(resultSet));
                }
                return List.copyOf(runs);
            }
        } catch (SQLException exception) {
            throw databaseFailure("Failed to list test runs", exception);
        }
    }

    private static List<DiffCoverageFileDetails> readDiffCoverageFiles(
            Connection connection,
            UUID diffId,
            boolean includeLines) throws SQLException {
        Map<String, List<DiffCoverageLineDetails>> linesByFilePath = includeLines
                ? readDiffCoverageLines(connection, diffId)
                : Map.of();
        try (var statement = connection.prepareStatement("""
                select file_path, old_file_path, change_status,
                       patch_line_covered, patch_line_total,
                       newly_missed_line_count, lost_coverage_line_count
                from vericov.pull_request_coverage_diff_files
                where pr_diff_id = ?
                order by file_path
                """)) {
            statement.setObject(1, diffId);
            try (var resultSet = statement.executeQuery()) {
                List<DiffCoverageFileDetails> files = new ArrayList<>();
                while (resultSet.next()) {
                    String filePath = resultSet.getString("file_path");
                    files.add(new DiffCoverageFileDetails(
                            filePath,
                            resultSet.getString("old_file_path"),
                            resultSet.getString("change_status"),
                            CoverageMetricDetails.of(
                                    resultSet.getInt("patch_line_covered"),
                                    resultSet.getInt("patch_line_total")),
                            resultSet.getInt("newly_missed_line_count"),
                            resultSet.getInt("lost_coverage_line_count"),
                            linesByFilePath.getOrDefault(filePath, List.of())));
                }
                return List.copyOf(files);
            }
        }
    }

    private static Map<String, List<DiffCoverageLineDetails>> readDiffCoverageLines(
            Connection connection,
            UUID diffId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select file_path, old_file_path, base_line_number, head_line_number,
                       change_type, executable, base_hits, head_hits,
                       newly_missed, lost_coverage
                from vericov.pull_request_coverage_diff_lines
                where pr_diff_id = ?
                order by file_path, coalesce(head_line_number, base_line_number), change_type
                """)) {
            statement.setObject(1, diffId);
            try (var resultSet = statement.executeQuery()) {
                Map<String, List<DiffCoverageLineDetails>> linesByFilePath = new LinkedHashMap<>();
                while (resultSet.next()) {
                    String filePath = resultSet.getString("file_path");
                    linesByFilePath.computeIfAbsent(filePath, ignored -> new ArrayList<>())
                            .add(new DiffCoverageLineDetails(
                                    filePath,
                                    resultSet.getString("old_file_path"),
                                    nullableInteger(resultSet, "base_line_number"),
                                    nullableInteger(resultSet, "head_line_number"),
                                    resultSet.getString("change_type"),
                                    resultSet.getBoolean("executable"),
                                    nullableLong(resultSet, "base_hits"),
                                    nullableLong(resultSet, "head_hits"),
                                    resultSet.getBoolean("newly_missed"),
                                    resultSet.getBoolean("lost_coverage")));
                }
                Map<String, List<DiffCoverageLineDetails>> immutableLines = new LinkedHashMap<>();
                linesByFilePath.forEach((filePath, lines) -> immutableLines.put(filePath, List.copyOf(lines)));
                return Map.copyOf(immutableLines);
            }
        }
    }

    private static UUID findCoverageReportId(Connection connection, UUID repositoryId, String commitSha)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                select id
                from vericov.coverage_reports
                where repository_id = ?
                  and commit_sha = ?
                  and status = 'complete'
                order by created_at desc, id desc
                limit 1
                """)) {
            statement.setObject(1, repositoryId);
            statement.setString(2, commitSha);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject("id", UUID.class) : null;
            }
        }
    }

    private static Map<Integer, Long> readCoverageLineHits(
            Connection connection,
            UUID coverageReportId,
            String filePath) throws SQLException {
        try (var statement = connection.prepareStatement("""
                select line_number, hits
                from vericov.coverage_line_hits
                where coverage_report_id = ?
                  and file_path = ?
                order by line_number
                """)) {
            statement.setObject(1, coverageReportId);
            statement.setString(2, filePath);
            try (var resultSet = statement.executeQuery()) {
                Map<Integer, Long> hitsByLine = new LinkedHashMap<>();
                while (resultSet.next()) {
                    hitsByLine.put(resultSet.getInt("line_number"), resultSet.getLong("hits"));
                }
                return Map.copyOf(hitsByLine);
            }
        }
    }

    private static void insertRepository(Connection connection, RepositoryDetails repository) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.repositories (
                    id,
                    tenant_id,
                    org_id,
                    provider,
                    provider_repository_id,
                    full_name,
                    default_branch,
                    visibility,
                    privacy_mode,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setObject(index++, repository.id());
            statement.setObject(index++, repository.tenantId());
            statement.setObject(index++, repository.organizationId());
            statement.setString(index++, repository.provider());
            statement.setString(index++, repository.providerRepositoryId());
            statement.setString(index++, repository.fullName());
            statement.setString(index++, repository.defaultBranch());
            statement.setString(index++, repository.visibility());
            statement.setString(index++, repository.privacyMode());
            statement.setString(index++, repository.status());
            statement.setObject(index++, utc(repository.createdAt()));
            statement.setObject(index, utc(repository.updatedAt()));
            statement.executeUpdate();
        }
    }

    private static OrganizationDetails readOrganization(ResultSet resultSet) throws SQLException {
        return new OrganizationDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("slug"),
                resultSet.getString("plan"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static MembershipDetails readMembership(ResultSet resultSet) throws SQLException {
        return new MembershipDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("supabase_user_id", UUID.class),
                resultSet.getString("role"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryDetails readRepository(ResultSet resultSet) throws SQLException {
        return new RepositoryDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getString("provider"),
                resultSet.getString("provider_repository_id"),
                resultSet.getString("full_name"),
                resultSet.getString("default_branch"),
                resultSet.getString("visibility"),
                resultSet.getString("privacy_mode"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static void setRepositoryComponent(
            PreparedStatement statement,
            Connection connection,
            RepositoryComponentDetails component) throws SQLException {
        int index = 1;
        statement.setObject(index++, component.id());
        statement.setObject(index++, component.tenantId());
        statement.setObject(index++, component.organizationId());
        statement.setObject(index++, component.repositoryId());
        statement.setString(index++, component.name());
        statement.setString(index++, component.description());
        statement.setArray(index++, connection.createArrayOf("text", component.pathPatterns().toArray(String[]::new)));
        statement.setArray(index++, connection.createArrayOf("text", component.owners().toArray(String[]::new)));
        statement.setString(index++, component.criticality());
        statement.setString(index++, jsonObject(component.metadata()));
        statement.setString(index++, component.status());
        statement.setObject(index++, component.createdByUserId());
        statement.setObject(index++, utc(component.createdAt()));
        statement.setObject(index, utc(component.updatedAt()));
    }

    private static RepositoryComponentDetails readRepositoryComponent(ResultSet resultSet) throws SQLException {
        return new RepositoryComponentDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("description"),
                stringArray(resultSet, "path_patterns"),
                stringArray(resultSet, "owners"),
                resultSet.getString("criticality"),
                jsonMap(resultSet, "metadata_json"),
                resultSet.getString("status"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryOwnerRuleDetails readRepositoryOwnerRule(ResultSet resultSet) throws SQLException {
        return new RepositoryOwnerRuleDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("source"),
                resultSet.getString("pattern"),
                stringArray(resultSet, "owners"),
                resultSet.getInt("priority"),
                resultSet.getString("source_ref"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryPackageNodeDetails readRepositoryPackageNode(ResultSet resultSet) throws SQLException {
        return new RepositoryPackageNodeDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("component_id", UUID.class),
                resultSet.getString("package_name"),
                resultSet.getString("package_path"),
                resultSet.getString("manifest_path"),
                resultSet.getString("ecosystem"),
                jsonMap(resultSet, "metadata_json"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static void setRepositoryApiKey(PreparedStatement statement, RepositoryApiKeyDetails apiKey)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, apiKey.id());
        statement.setObject(index++, apiKey.tenantId());
        statement.setObject(index++, apiKey.repositoryId());
        statement.setString(index++, apiKey.name());
        statement.setString(index++, apiKey.keyPrefix());
        statement.setString(index++, apiKey.keyHash());
        statement.setArray(index++, statement.getConnection().createArrayOf("text", apiKey.scopes().toArray(String[]::new)));
        statement.setArray(index++, statement.getConnection()
                .createArrayOf("text", apiKey.branchAllowPatterns().toArray(String[]::new)));
        setNullableInstant(statement, index++, apiKey.expiresAt());
        setNullableInstant(statement, index++, apiKey.revokedAt());
        statement.setObject(index++, apiKey.createdByUserId());
        statement.setObject(index++, apiKey.revokedByUserId());
        setNullableInstant(statement, index++, apiKey.lastUsedAt());
        statement.setObject(index++, utc(apiKey.createdAt()));
        statement.setObject(index, utc(apiKey.updatedAt()));
    }

    private static RepositoryApiKeyDetails readRepositoryApiKey(ResultSet resultSet) throws SQLException {
        return new RepositoryApiKeyDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("key_prefix"),
                resultSet.getString("key_hash"),
                null,
                stringArray(resultSet, "scopes"),
                stringArray(resultSet, "branch_allow_patterns"),
                nullableInstant(resultSet, "expires_at"),
                nullableInstant(resultSet, "revoked_at"),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getObject("revoked_by_user_id", UUID.class),
                nullableInstant(resultSet, "last_used_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static void bindRepositoryConfig(PreparedStatement statement, RepositoryConfigDetails config)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, config.id());
        statement.setObject(index++, config.tenantId());
        statement.setObject(index++, config.organizationId());
        statement.setObject(index++, config.repositoryId());
        statement.setString(index++, config.source());
        statement.setString(index++, jsonObject(config.config()));
        statement.setInt(index++, config.schemaVersion());
        statement.setString(index++, config.validationStatus());
        statement.setString(index++, jsonStringArray(config.validationErrors()));
        statement.setObject(index++, config.updatedByUserId());
        statement.setObject(index++, utc(config.createdAt()));
        statement.setObject(index, utc(config.updatedAt()));
    }

    private static void bindRepositoryPolicy(PreparedStatement statement, RepositoryPolicyDetails policy)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, policy.id());
        statement.setObject(index++, policy.tenantId());
        statement.setObject(index++, policy.organizationId());
        statement.setObject(index++, policy.repositoryId());
        statement.setString(index++, policy.name());
        statement.setString(index++, policy.description());
        statement.setString(index++, policy.policyType());
        statement.setString(index++, policy.targetType());
        statement.setString(index++, policy.targetSelector());
        statement.setString(index++, jsonObject(policy.config()));
        statement.setString(index++, policy.status());
        statement.setInt(index++, policy.priority());
        statement.setObject(index++, policy.createdByUserId());
        statement.setObject(index++, utc(policy.createdAt()));
        statement.setObject(index, utc(policy.updatedAt()));
    }

    private static void bindRepositoryBadgeSettings(
            PreparedStatement statement,
            RepositoryBadgeSettingsDetails settings) throws SQLException {
        int index = 1;
        statement.setObject(index++, settings.id());
        statement.setObject(index++, settings.tenantId());
        statement.setObject(index++, settings.organizationId());
        statement.setObject(index++, settings.repositoryId());
        statement.setBoolean(index++, settings.enabled());
        statement.setString(index++, settings.branch());
        statement.setString(index++, settings.metric());
        statement.setString(index++, settings.label());
        statement.setString(index++, jsonObject(settings.thresholds()));
        statement.setString(index++, settings.tokenHash());
        statement.setString(index++, settings.tokenPrefix());
        statement.setObject(index++, settings.createdByUserId());
        statement.setObject(index++, utc(settings.createdAt()));
        statement.setObject(index++, utc(settings.updatedAt()));
        setNullableInstant(statement, index, settings.revokedAt());
    }

    private static void bindCoverageBadgeCache(PreparedStatement statement, CoverageBadgeCacheEntry entry)
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entry.id());
        statement.setObject(index++, entry.tenantId());
        statement.setObject(index++, entry.organizationId());
        statement.setObject(index++, entry.repositoryId());
        statement.setString(index++, entry.cacheScope());
        statement.setString(index++, entry.branch());
        statement.setString(index++, entry.metric());
        statement.setString(index++, entry.label());
        statement.setString(index++, entry.message());
        statement.setString(index++, entry.color());
        statement.setString(index++, entry.commitSha());
        statement.setBigDecimal(index++, entry.coveragePercent());
        statement.setObject(index++, entry.sourceReportId());
        setNullableInstant(statement, index++, entry.reportCreatedAt());
        statement.setObject(index++, utc(entry.settingsUpdatedAt()));
        statement.setObject(index++, utc(entry.cachedAt()));
        statement.setObject(index, utc(entry.expiresAt()));
    }

    private static void insertRepositoryGate(Connection connection, RepositoryGateDetails gate) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into vericov.repository_gate_configurations (
                    id,
                    tenant_id,
                    org_id,
                    repository_id,
                    name,
                    gate_type,
                    metric,
                    threshold,
                    max_drop,
                    blocking,
                    config_json,
                    status,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setObject(index++, gate.id());
            statement.setObject(index++, gate.tenantId());
            statement.setObject(index++, gate.organizationId());
            statement.setObject(index++, gate.repositoryId());
            statement.setString(index++, gate.name());
            statement.setString(index++, gate.gateType());
            statement.setString(index++, gate.metric());
            statement.setBigDecimal(index++, gate.threshold());
            statement.setBigDecimal(index++, gate.maxDrop());
            statement.setBoolean(index++, gate.blocking());
            statement.setString(index++, jsonObject(gate.config()));
            statement.setString(index++, gate.status());
            statement.setObject(index++, utc(gate.createdAt()));
            statement.setObject(index, utc(gate.updatedAt()));
            statement.executeUpdate();
        }
    }

    private static PolicyDefaultsDetails readPolicyDefaults(ResultSet resultSet) throws SQLException {
        return new PolicyDefaultsDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                jsonMap(resultSet, "defaults_json"),
                resultSet.getInt("schema_version"),
                resultSet.getObject("updated_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryConfigDetails readRepositoryConfig(ResultSet resultSet) throws SQLException {
        return new RepositoryConfigDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("source"),
                jsonMap(resultSet, "config_json"),
                resultSet.getInt("schema_version"),
                resultSet.getString("validation_status"),
                jsonStringList(resultSet, "validation_errors"),
                resultSet.getObject("updated_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryPolicyDetails readRepositoryPolicy(ResultSet resultSet) throws SQLException {
        return new RepositoryPolicyDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("policy_type"),
                resultSet.getString("target_type"),
                resultSet.getString("target_selector"),
                jsonMap(resultSet, "config_json"),
                resultSet.getString("status"),
                resultSet.getInt("priority"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryGateDetails readRepositoryGate(ResultSet resultSet) throws SQLException {
        return new RepositoryGateDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("gate_type"),
                resultSet.getString("metric"),
                resultSet.getBigDecimal("threshold"),
                resultSet.getBigDecimal("max_drop"),
                resultSet.getBoolean("blocking"),
                jsonMap(resultSet, "config_json"),
                resultSet.getString("status"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static RepositoryBadgeSettingsDetails readRepositoryBadgeSettings(ResultSet resultSet)
            throws SQLException {
        return new RepositoryBadgeSettingsDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getBoolean("enabled"),
                resultSet.getString("branch"),
                resultSet.getString("metric"),
                resultSet.getString("label"),
                jsonMap(resultSet, "thresholds_json"),
                resultSet.getString("token_hash"),
                resultSet.getString("token_prefix"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                nullableInstant(resultSet, "revoked_at"));
    }

    private static CoverageBadgeCacheEntry readCoverageBadgeCacheEntry(ResultSet resultSet) throws SQLException {
        return new CoverageBadgeCacheEntry(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("cache_scope"),
                resultSet.getString("branch"),
                resultSet.getString("metric"),
                resultSet.getString("label"),
                resultSet.getString("message"),
                resultSet.getString("color"),
                resultSet.getString("commit_sha"),
                resultSet.getBigDecimal("coverage_percent"),
                resultSet.getObject("source_report_id", UUID.class),
                nullableInstant(resultSet, "report_created_at"),
                instant(resultSet, "settings_updated_at"),
                instant(resultSet, "cached_at"),
                instant(resultSet, "expires_at"));
    }

    private static CoverageReportSummary readCoverageReportSummary(ResultSet resultSet) throws SQLException {
        return new CoverageReportSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("upload_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                (Integer) resultSet.getObject("pull_request_number"),
                resultSet.getInt("line_covered"),
                resultSet.getInt("line_total"),
                resultSet.getInt("branch_covered"),
                resultSet.getInt("branch_total"),
                resultSet.getInt("function_covered"),
                resultSet.getInt("function_total"),
                resultSet.getInt("statement_covered"),
                resultSet.getInt("statement_total"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static CoverageFileSummaryDetails readCoverageFileSummary(ResultSet resultSet) throws SQLException {
        return new CoverageFileSummaryDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("coverage_report_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("file_path"),
                resultSet.getInt("line_covered"),
                resultSet.getInt("line_total"),
                resultSet.getInt("branch_covered"),
                resultSet.getInt("branch_total"),
                resultSet.getInt("function_covered"),
                resultSet.getInt("function_total"),
                resultSet.getInt("statement_covered"),
                resultSet.getInt("statement_total"),
                instant(resultSet, "created_at"));
    }

    private static TestRunDetails readTestRun(ResultSet resultSet) throws SQLException {
        return new TestRunDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("upload_id", UUID.class),
                resultSet.getObject("upload_artifact_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                (Integer) resultSet.getObject("pull_request_number"),
                resultSet.getString("suite_name"),
                resultSet.getInt("suite_index"),
                resultSet.getString("status"),
                resultSet.getInt("total_count"),
                resultSet.getInt("passed_count"),
                resultSet.getInt("failed_count"),
                resultSet.getInt("error_count"),
                resultSet.getInt("skipped_count"),
                resultSet.getLong("duration_ms"),
                instant(resultSet, "created_at"));
    }

    private static GateEvaluationDetails readGateEvaluation(ResultSet resultSet) throws SQLException {
        return new GateEvaluationDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("coverage_report_id", UUID.class),
                resultSet.getString("commit_sha"),
                resultSet.getString("branch"),
                (Integer) resultSet.getObject("pull_request_number"),
                resultSet.getString("gate_name"),
                resultSet.getString("gate_type"),
                resultSet.getString("metric"),
                resultSet.getBigDecimal("threshold"),
                resultSet.getBigDecimal("actual"),
                resultSet.getString("status"),
                resultSet.getBoolean("blocking"),
                jsonMap(resultSet, "details_json"),
                instant(resultSet, "evaluated_at"));
    }

    private static CoverageGapFindingDetails readCoverageGap(ResultSet resultSet) throws SQLException {
        return new CoverageGapFindingDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("org_id", UUID.class),
                resultSet.getObject("repository_id", UUID.class),
                resultSet.getObject("coverage_report_id", UUID.class),
                resultSet.getObject("pr_diff_id", UUID.class),
                resultSet.getObject("component_id", UUID.class),
                resultSet.getString("commit_sha"),
                nullableInteger(resultSet, "pull_request_number"),
                resultSet.getString("file_path"),
                resultSet.getString("target_type"),
                nullableInteger(resultSet, "line_start"),
                nullableInteger(resultSet, "line_end"),
                resultSet.getString("symbol_name"),
                resultSet.getString("reason_code"),
                resultSet.getString("explanation"),
                resultSet.getString("confidence"),
                resultSet.getBigDecimal("risk_score"),
                resultSet.getString("risk_level"),
                stringArray(resultSet, "owners"),
                resultSet.getString("next_action"),
                resultSet.getString("status"),
                jsonMap(resultSet, "evidence_json"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static Map<String, Object> jsonMap(ResultSet resultSet, String columnName) throws SQLException {
        String raw = resultSet.getString(columnName);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try (var reader = Json.createReader(new StringReader(raw))) {
            return plainJsonObject(reader.readObject());
        }
    }

    private static List<String> jsonStringList(ResultSet resultSet, String columnName) throws SQLException {
        String raw = resultSet.getString(columnName);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try (var reader = Json.createReader(new StringReader(raw))) {
            List<String> values = new ArrayList<>();
            for (JsonValue value : reader.readArray()) {
                if (value instanceof JsonString stringValue) {
                    values.add(stringValue.getString());
                } else {
                    values.add(value.toString());
                }
            }
            return List.copyOf(values);
        }
    }

    private static String jsonObject(Map<String, Object> values) {
        return jsonObjectBuilder(values).build().toString();
    }

    private static String jsonStringArray(List<String> values) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        if (values != null) {
            values.forEach(builder::add);
        }
        return builder.build().toString();
    }

    private static JsonObjectBuilder jsonObjectBuilder(Map<?, ?> values) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (values == null) {
            return builder;
        }
        values.forEach((key, value) -> {
            if (!(key instanceof String stringKey)) {
                throw new OrganizationException("validation_error", "config key must be a string");
            }
            builder.add(stringKey, jsonValue(value));
        });
        return builder;
    }

    private static JsonArrayBuilder jsonArrayBuilder(List<?> values) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        if (values != null) {
            values.forEach(value -> builder.add(jsonValue(value)));
        }
        return builder;
    }

    private static JsonValue jsonValue(Object value) {
        if (value == null) {
            return JsonValue.NULL;
        }
        if (value instanceof String stringValue) {
            return Json.createValue(stringValue);
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (value instanceof Number numberValue) {
            return Json.createValue(new BigDecimal(numberValue.toString()));
        }
        if (value instanceof Map<?, ?> mapValue) {
            return jsonObjectBuilder(mapValue).build();
        }
        if (value instanceof List<?> listValue) {
            return jsonArrayBuilder(listValue).build();
        }
        throw new OrganizationException("validation_error", "config value type is invalid");
    }

    private static Map<String, Object> plainJsonObject(JsonObject object) {
        Map<String, Object> values = new LinkedHashMap<>();
        object.forEach((key, value) -> values.put(key, plainJsonValue(value)));
        return Map.copyOf(values);
    }

    private static Object plainJsonValue(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> plainJsonObject(value.asJsonObject());
            case ARRAY -> value.asJsonArray().stream()
                    .map(JdbcOrganizationRepository::plainJsonValue)
                    .toList();
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> ((JsonNumber) value).bigDecimalValue();
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getObject(columnName, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        OffsetDateTime value = resultSet.getObject(columnName, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        Number value = (Number) resultSet.getObject(columnName);
        return value == null ? null : value.intValue();
    }

    private static Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        Number value = (Number) resultSet.getObject(columnName);
        return value == null ? null : value.longValue();
    }

    private static List<String> stringArray(ResultSet resultSet, String columnName) throws SQLException {
        java.sql.Array array = resultSet.getArray(columnName);
        if (array == null) {
            return List.of();
        }
        Object values = array.getArray();
        if (values instanceof String[] strings) {
            return List.of(strings);
        }
        Object[] objects = (Object[]) values;
        List<String> strings = new ArrayList<>(objects.length);
        for (Object object : objects) {
            strings.add(String.valueOf(object));
        }
        return List.copyOf(strings);
    }

    private static void setNullableInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setObject(index, value == null ? null : utc(value));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static RuntimeException mapIntegrityFailure(String message, SQLException exception) {
        if ("23505".equals(exception.getSQLState()) || exception instanceof SQLIntegrityConstraintViolationException) {
            return new OrganizationException("conflict", "Unique control-plane resource constraint failed");
        }
        return databaseFailure(message, exception);
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
