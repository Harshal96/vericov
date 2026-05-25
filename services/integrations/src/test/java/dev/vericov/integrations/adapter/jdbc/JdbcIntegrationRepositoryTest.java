package dev.vericov.integrations.adapter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.integrations.application.IntegrationBindingDetails;
import dev.vericov.integrations.application.IntegrationConnectionDetails;
import dev.vericov.integrations.application.IntegrationCredentialDetails;
import dev.vericov.integrations.application.IntegrationEventDetails;
import dev.vericov.integrations.application.IntegrationException;
import dev.vericov.integrations.application.IntegrationSyncStateDetails;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcIntegrationRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private DataSource dataSource;
    private JdbcIntegrationRepository repository;
    private UUID tenantId;
    private UUID orgId;

    @BeforeEach
    void setUp() throws SQLException {
        String jdbcUrl = env("VERICOV_TEST_DATABASE_URL", "");
        Assumptions.assumeFalse(jdbcUrl.isBlank(), "VERICOV_TEST_DATABASE_URL is not configured");
        dataSource = new DriverManagerDataSource(
                jdbcUrl,
                env("VERICOV_TEST_DATABASE_USER", env("SUPABASE_DB_USER", "")),
                env("VERICOV_TEST_DATABASE_PASSWORD", env("SUPABASE_DB_PASSWORD", "")));
        repository = new JdbcIntegrationRepository(dataSource, new IntegrationJsonCodec());
        tenantId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        insertTenantAndOrganization();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (dataSource == null || tenantId == null) {
            return;
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("delete from vericov.tenants where id = ?")) {
            statement.setObject(1, tenantId);
            statement.executeUpdate();
        }
    }

    @Test
    void savesAndFetchesConnection() {
        IntegrationConnectionDetails saved = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Engineering GitHub",
                "acct-1",
                "active",
                Map.of("installation_id", "123456", "limits", Map.of("max", 5))));

        IntegrationConnectionDetails fetched = repository.findConnection(tenantId, orgId, saved.id()).orElseThrow();

        assertEquals(saved, fetched);
        assertEquals(List.of(saved), repository.listConnections(tenantId, orgId));
        assertEquals(saved, repository.findActiveConnection(tenantId, orgId, "github", "acct-1").orElseThrow());
        assertEquals(Map.of("max", 5), fetched.config().get("limits"));
    }

    @Test
    void duplicateConnectionIdReturnsSpecificConflict() {
        IntegrationConnectionDetails first = repository.saveConnection(connection(
                UUID.randomUUID(),
                "ID GitHub",
                "acct-duplicate-id",
                "active",
                Map.of()));
        IntegrationConnectionDetails duplicateId = connection(
                first.id(),
                "Other GitHub",
                "acct-other",
                "active",
                Map.of());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.saveConnection(duplicateId));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void duplicateActiveConnectionReturnsSpecificConflict() {
        repository.saveConnection(connection(
                UUID.randomUUID(),
                "Active GitHub",
                "acct-active",
                "active",
                Map.of()));
        IntegrationConnectionDetails duplicateActive = connection(
                UUID.randomUUID(),
                "Duplicate Active GitHub",
                "acct-active",
                "active",
                Map.of());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.saveConnection(duplicateActive));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void savesAndResolvesBindingWithinConnectionScope() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Engineering GitHub",
                "acct-2",
                "active",
                Map.of()));
        UUID repositoryId = UUID.randomUUID();
        IntegrationBindingDetails binding = binding(
                UUID.randomUUID(),
                connection.id(),
                repositoryId,
                List.of("git.checks", "git.comments"),
                Map.of("repository", "vericov/app"),
                "active");

        IntegrationBindingDetails saved = repository.upsertBinding(binding, null);
        repository.saveCredential(credential(
                UUID.randomUUID(),
                connection.id(),
                "github_app_private_key",
                "vault://test/github-app-private-key",
                "active"));

        assertEquals(saved, repository.findBinding(tenantId, orgId, connection.id(), "repository", repositoryId)
                .orElseThrow());
        assertEquals(List.of(saved), repository.listBindings(tenantId, orgId, connection.id()));
        assertEquals("github_app_private_key", repository.resolve(
                        tenantId,
                        orgId,
                        "github",
                        "repository",
                        repositoryId,
                        "git.checks",
                        "github_app_private_key")
                .orElseThrow()
                .credentialKind());
        assertTrue(repository.resolve(
                        tenantId,
                        orgId,
                        "github",
                        "repository",
                        repositoryId,
                        "git.webhooks",
                        "webhook_secret")
                .isEmpty());
    }

    @Test
    void updatesBindingAndRejectsStaleBindingUpsert() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Binding GitHub",
                "acct-binding",
                "active",
                Map.of()));
        UUID repositoryId = UUID.randomUUID();
        IntegrationBindingDetails current = repository.upsertBinding(binding(
                UUID.randomUUID(),
                connection.id(),
                repositoryId,
                List.of("git.checks"),
                Map.of("count", 1),
                "active"), null);
        IntegrationBindingDetails update = new IntegrationBindingDetails(
                UUID.randomUUID(),
                tenantId,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.comments"),
                Map.of("count", 2),
                "disabled",
                NOW,
                current.updatedAt());

        IntegrationBindingDetails updated = repository.upsertBinding(update, current.updatedAt());

        assertEquals(current.id(), updated.id());
        assertEquals(current.createdAt(), updated.createdAt());
        assertEquals(List.of("git.comments"), updated.capabilities());
        assertEquals(Map.of("count", 2), updated.config());
        assertEquals("disabled", updated.status());
        assertTrue(updated.updatedAt().isAfter(current.updatedAt()));
        assertNotEquals(current.updatedAt(), updated.updatedAt());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.upsertBinding(update, current.updatedAt()));

        assertEquals("conflict", exception.code());
        assertEquals("Integration binding was modified", exception.getMessage());
    }

    @Test
    void savesCredentialMetadataWithoutRawSecret() throws SQLException {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Credential GitHub",
                "acct-3",
                "active",
                Map.of()));
        String rawSecret = "raw-token-value";
        String secretRef = "vault://test/access-token";
        IntegrationCredentialDetails credential = credential(
                UUID.randomUUID(),
                connection.id(),
                "oauth_access_token",
                secretRef,
                "active");

        IntegrationCredentialDetails saved = repository.saveCredential(credential);

        assertEquals(saved, repository.findActiveCredential(tenantId, orgId, connection.id(), "oauth_access_token")
                .orElseThrow());
        assertEquals(List.of(saved), repository.listCredentials(tenantId, orgId, connection.id()));
        assertFalse(saved.secretRef().contains(rawSecret));
        assertFalse(saved.toString().contains(rawSecret));
        assertEquals(secretRef, saved.secretRef());
        assertFalse(integrationCredentialColumnNames().contains("raw_secret"));
        assertFalse(integrationCredentialColumnNames().contains("secret_value"));
        assertFalse(storedCredentialMetadata(saved.id()).contains(rawSecret));
    }

    @Test
    void duplicateCredentialIdReturnsSpecificConflict() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Credential ID GitHub",
                "acct-credential-id",
                "active",
                Map.of()));
        IntegrationCredentialDetails first = repository.saveCredential(credential(
                UUID.randomUUID(),
                connection.id(),
                "oauth_access_token",
                "vault://test/first",
                "active"));
        IntegrationCredentialDetails duplicateId = credential(
                first.id(),
                connection.id(),
                "webhook_secret",
                "vault://test/second",
                "active");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.saveCredential(duplicateId));

        assertEquals("conflict", exception.code());
        assertEquals("Integration credential already exists", exception.getMessage());
    }

    @Test
    void duplicateActiveCredentialReturnsSpecificConflict() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Credential Active GitHub",
                "acct-credential-active",
                "active",
                Map.of()));
        repository.saveCredential(credential(
                UUID.randomUUID(),
                connection.id(),
                "oauth_access_token",
                "vault://test/first-active",
                "active"));
        IntegrationCredentialDetails duplicateActive = credential(
                UUID.randomUUID(),
                connection.id(),
                "oauth_access_token",
                "vault://test/second-active",
                "active");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.saveCredential(duplicateActive));

        assertEquals("conflict", exception.code());
        assertEquals("Integration credential already exists", exception.getMessage());
    }

    @Test
    void upsertsSyncStateByConnectionScopeAndType() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Sync GitHub",
                "acct-sync",
                "active",
                Map.of()));
        UUID repositoryId = UUID.randomUUID();
        IntegrationSyncStateDetails first = repository.upsertSyncState(syncState(
                UUID.randomUUID(),
                connection.id(),
                "repository_full",
                "repository",
                repositoryId,
                "running",
                Map.of("cursor", "a"),
                Map.of("page", 1),
                Map.of(),
                NOW));
        IntegrationSyncStateDetails second = repository.upsertSyncState(syncState(
                UUID.randomUUID(),
                connection.id(),
                "repository_full",
                "repository",
                repositoryId,
                "succeeded",
                Map.of("cursor", "b"),
                Map.of("page", 2),
                Map.of(),
                NOW.plusSeconds(5)));

        assertEquals(first.id(), second.id());
        assertEquals(first.createdAt(), second.createdAt());
        assertEquals("succeeded", second.status());
        assertEquals(Map.of("cursor", "b"), second.cursor());
        assertEquals(Map.of("page", 2), second.checkpoint());
        assertTrue(second.updatedAt().isAfter(first.updatedAt()));
    }

    @Test
    void recordsIntegrationEventWithConnectionOwnership() {
        IntegrationConnectionDetails connection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Event GitHub",
                "acct-event",
                "active",
                Map.of()));
        UUID repositoryId = UUID.randomUUID();
        IntegrationEventDetails event = event(
                UUID.randomUUID(),
                connection.id(),
                "github",
                "sync.completed",
                "evt-1",
                "repository",
                repositoryId,
                "processed",
                Map.of("count", 3),
                Map.of());

        IntegrationEventDetails saved = repository.recordEvent(event);

        assertEquals(event.id(), saved.id());
        assertEquals(connection.id(), saved.connectionId());
        assertEquals("github", saved.providerKey());
        assertEquals(Map.of("count", 3), saved.payload());
        assertEquals("processed", saved.status());
    }

    @Test
    void staleConnectionUpdateReturnsConflict() {
        IntegrationConnectionDetails current = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Stale GitHub",
                "acct-4",
                "active",
                Map.of()));
        IntegrationConnectionDetails firstUpdateRequest = new IntegrationConnectionDetails(
                current.id(),
                current.tenantId(),
                current.orgId(),
                current.providerKey(),
                current.integrationType(),
                "Updated GitHub",
                current.externalAccountId(),
                current.externalAccountName(),
                current.status(),
                current.config(),
                current.createdBy(),
                current.lastVerifiedAt(),
                current.createdAt(),
                current.updatedAt().plusNanos(1));
        IntegrationConnectionDetails firstUpdate = repository.updateConnection(firstUpdateRequest, current.updatedAt());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.updateConnection(firstUpdateRequest, current.updatedAt()));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection was modified", exception.getMessage());
        assertEquals(current.updatedAt().plusNanos(1_000), firstUpdate.updatedAt());
    }

    @Test
    void ambiguousResolutionReturnsConflict() {
        IntegrationConnectionDetails firstConnection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "First GitHub",
                "acct-5",
                "active",
                Map.of()));
        IntegrationConnectionDetails secondConnection = repository.saveConnection(connection(
                UUID.randomUUID(),
                "Second GitHub",
                "acct-6",
                "active",
                Map.of()));
        UUID repositoryId = UUID.randomUUID();
        repository.upsertBinding(binding(
                UUID.randomUUID(),
                firstConnection.id(),
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"), null);
        repository.upsertBinding(binding(
                UUID.randomUUID(),
                secondConnection.id(),
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"), null);
        repository.saveCredential(credential(
                UUID.randomUUID(),
                firstConnection.id(),
                "github_app_private_key",
                "vault://test/first-key",
                "active"));
        repository.saveCredential(credential(
                UUID.randomUUID(),
                secondConnection.id(),
                "github_app_private_key",
                "vault://test/second-key",
                "active"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> repository.resolve(
                        tenantId,
                        orgId,
                        "github",
                        "repository",
                        repositoryId,
                        "git.checks",
                        "github_app_private_key"));

        assertEquals("conflict", exception.code());
        assertEquals("Integration resolution is ambiguous", exception.getMessage());
    }

    private void insertTenantAndOrganization() throws SQLException {
        String slug = "jdbc-" + tenantId.toString().replace("-", "");
        try (var connection = dataSource.getConnection();
                var tenantStatement = connection.prepareStatement("""
                        insert into vericov.tenants (id, name, slug)
                        values (?, ?, ?)
                        """);
                var organizationStatement = connection.prepareStatement("""
                        insert into vericov.organizations (id, tenant_id, name, slug)
                        values (?, ?, ?, ?)
                        """)) {
            tenantStatement.setObject(1, tenantId);
            tenantStatement.setString(2, "JDBC Test Tenant");
            tenantStatement.setString(3, slug);
            tenantStatement.executeUpdate();
            organizationStatement.setObject(1, orgId);
            organizationStatement.setObject(2, tenantId);
            organizationStatement.setString(3, "JDBC Test Organization");
            organizationStatement.setString(4, slug + "-org");
            organizationStatement.executeUpdate();
        }
    }

    private List<String> integrationCredentialColumnNames() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select column_name
                        from information_schema.columns
                        where table_schema = 'vericov'
                          and table_name = 'integration_credentials'
                        order by ordinal_position
                        """);
                var resultSet = statement.executeQuery()) {
            List<String> columns = new ArrayList<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            assertTrue(columns.contains("secret_ref"));
            return List.copyOf(columns);
        }
    }

    private String storedCredentialMetadata(UUID credentialId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id, credential_kind, secret_ref, status
                        from vericov.integration_credentials
                        where id = ?
                        """)) {
            statement.setObject(1, credentialId);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getObject("id", UUID.class)
                        + "|"
                        + resultSet.getString("credential_kind")
                        + "|"
                        + resultSet.getString("secret_ref")
                        + "|"
                        + resultSet.getString("status");
            }
        }
    }

    private IntegrationConnectionDetails connection(
            UUID id,
            String displayName,
            String externalAccountId,
            String status,
            Map<String, Object> config) {
        return new IntegrationConnectionDetails(
                id,
                tenantId,
                orgId,
                "github",
                "git",
                displayName,
                externalAccountId,
                "Vericov",
                status,
                config,
                REQUESTER_ID,
                NOW.minusSeconds(60),
                NOW,
                NOW);
    }

    private IntegrationBindingDetails binding(
            UUID id,
            UUID connectionId,
            UUID scopeId,
            List<String> capabilities,
            Map<String, Object> config,
            String status) {
        return new IntegrationBindingDetails(
                id,
                tenantId,
                connectionId,
                "repository",
                scopeId,
                capabilities,
                config,
                status,
                NOW,
                NOW);
    }

    private IntegrationCredentialDetails credential(
            UUID id,
            UUID connectionId,
            String credentialKind,
            String secretRef,
            String status) {
        return new IntegrationCredentialDetails(
                id,
                tenantId,
                connectionId,
                credentialKind,
                secretRef,
                1,
                status,
                NOW.plusSeconds(3600),
                NOW,
                NOW,
                NOW);
    }

    private IntegrationSyncStateDetails syncState(
            UUID id,
            UUID connectionId,
            String syncType,
            String scopeType,
            UUID scopeId,
            String status,
            Map<String, Object> cursor,
            Map<String, Object> checkpoint,
            Map<String, Object> lastError,
            Instant updatedAt) {
        return new IntegrationSyncStateDetails(
                id,
                tenantId,
                orgId,
                connectionId,
                syncType,
                scopeType,
                scopeId,
                status,
                cursor,
                checkpoint,
                lastError,
                NOW.minusSeconds(60),
                status.equals("succeeded") ? updatedAt : null,
                updatedAt.plusSeconds(300),
                updatedAt.plusSeconds(120),
                NOW,
                updatedAt);
    }

    private IntegrationEventDetails event(
            UUID id,
            UUID connectionId,
            String providerKey,
            String eventType,
            String externalEventId,
            String scopeType,
            UUID scopeId,
            String status,
            Map<String, Object> payload,
            Map<String, Object> error) {
        return new IntegrationEventDetails(
                id,
                tenantId,
                orgId,
                connectionId,
                null,
                providerKey,
                eventType,
                externalEventId,
                scopeType,
                scopeId,
                status,
                payload,
                error,
                NOW,
                NOW.plusSeconds(1),
                NOW,
                NOW);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
