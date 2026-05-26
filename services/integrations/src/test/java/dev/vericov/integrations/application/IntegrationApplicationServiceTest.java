package dev.vericov.integrations.application;

import dev.vericov.integrations.application.port.CredentialVault;
import dev.vericov.integrations.application.port.IntegrationScopeValidator;
import dev.vericov.integrations.config.StaticProviderRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationApplicationServiceTest {
    private static final UUID REQUESTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_ORG_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID REPOSITORY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID OTHER_REPOSITORY_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID COMPONENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");

    @Test
    void createsGithubConnectionWithNormalizedValues() {
        TestFixture fixture = new TestFixture();

        IntegrationConnectionDetails connection = fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                " GitHub ",
                "  Engineering GitHub  ",
                " 123456 ",
                " Vericov ",
                Map.of("installation_id", "123456")));

        assertEquals("github", connection.providerKey());
        assertEquals("git", connection.integrationType());
        assertEquals("active", connection.status());
        assertEquals("Engineering GitHub", connection.displayName());
        assertEquals("123456", connection.externalAccountId());
        assertEquals("Vericov", connection.externalAccountName());
        assertEquals(REQUESTER_ID, connection.createdBy());
        assertEquals(NOW, connection.createdAt());
        assertEquals(NOW, connection.updatedAt());
    }

    @Test
    void rejectsDuplicateActiveProviderConnectionForOrgAndExternalAccount() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        "github",
                        "Backup GitHub",
                        "123456",
                        "Vericov",
                        Map.of())));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void allowsSameActiveProviderConnectionInDifferentTenantButRejectsWithinSameTenantAndOrg() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();

        IntegrationConnectionDetails otherTenantConnection = fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                OTHER_TENANT_ID,
                ORG_ID,
                "github",
                "Other Tenant GitHub",
                "123456",
                "Vericov",
                Map.of()));
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        "github",
                        "Duplicate GitHub",
                        "123456",
                        "Vericov",
                        Map.of())));

        assertEquals(OTHER_TENANT_ID, otherTenantConnection.tenantId());
        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void rejectsReactivatingDuplicateProviderConnectionForOrgAndExternalAccount() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();
        IntegrationConnectionDetails disabledDuplicate = fixture.saveConnection(
                "00000000-0000-0000-0000-000000000222",
                "Duplicate GitHub",
                "disabled");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        disabledDuplicate.id(),
                        null,
                        "active",
                        Map.of(),
                        disabledDuplicate.updatedAt())));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void repositoryRejectsDuplicateActiveConnectionDuringUpdate() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();
        IntegrationConnectionDetails disabledDuplicate = fixture.saveConnection(
                "00000000-0000-0000-0000-000000000222",
                "Duplicate GitHub",
                "disabled");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.repository.updateConnection(disabledDuplicate.withStatus("active", NOW), NOW));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void repositoryRejectsDuplicateActiveConnectionDuringSaveInSameTenantAndOrg() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.saveConnection(
                        TENANT_ID,
                        "00000000-0000-0000-0000-000000000333",
                        "Duplicate GitHub",
                        "active"));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection already exists", exception.getMessage());
    }

    @Test
    void updateDuplicateDetectionIsTenantScoped() {
        TestFixture fixture = new TestFixture();
        fixture.createGithubConnection();
        IntegrationConnectionDetails otherTenantDisabledDuplicate = fixture.saveConnection(
                OTHER_TENANT_ID,
                "00000000-0000-0000-0000-000000000333",
                "Other Tenant GitHub",
                "disabled");

        IntegrationConnectionDetails activated = fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                OTHER_TENANT_ID,
                ORG_ID,
                otherTenantDisabledDuplicate.id(),
                null,
                "active",
                Map.of(),
                otherTenantDisabledDuplicate.updatedAt()));

        assertEquals(OTHER_TENANT_ID, activated.tenantId());
        assertEquals("active", activated.status());
    }

    @Test
    void scopedReadUpdateAndDisableReturnNotFoundForWrongTenantOrOrg() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        assertNotFound(() -> fixture.service.getConnection(REQUESTER_ID, OTHER_TENANT_ID, ORG_ID, connection.id()));
        assertNotFound(() -> fixture.service.getConnection(REQUESTER_ID, TENANT_ID, OTHER_ORG_ID, connection.id()));
        assertNotFound(() -> fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                OTHER_TENANT_ID,
                ORG_ID,
                connection.id(),
                "Renamed GitHub",
                null,
                Map.of(),
                connection.updatedAt())));
        assertNotFound(() -> fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                OTHER_ORG_ID,
                connection.id(),
                "Renamed GitHub",
                null,
                Map.of(),
                connection.updatedAt())));
        assertNotFound(() -> fixture.service.disableConnection(
                REQUESTER_ID,
                OTHER_TENANT_ID,
                ORG_ID,
                connection.id(),
                connection.updatedAt()));
        assertNotFound(() -> fixture.service.disableConnection(
                REQUESTER_ID,
                TENANT_ID,
                OTHER_ORG_ID,
                connection.id(),
                connection.updatedAt()));
    }

    @Test
    void repositoryRejectsStaleConnectionUpdate() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails original = fixture.createGithubConnection();
        IntegrationConnectionDetails firstUpdate = original.withValues(
                "First GitHub",
                "active",
                original.config(),
                NOW.plusSeconds(1));
        IntegrationConnectionDetails staleSecondUpdate = original.withValues(
                "Second GitHub",
                "active",
                original.config(),
                NOW.plusSeconds(2));
        fixture.repository.updateConnection(firstUpdate, original.updatedAt());

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.repository.updateConnection(staleSecondUpdate, original.updatedAt()));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection was modified", exception.getMessage());
        assertEquals("First GitHub", fixture.repository
                .findConnection(TENANT_ID, ORG_ID, original.id())
                .orElseThrow()
                .displayName());
    }

    @Test
    void serviceRejectsStaleUpdateToken() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails original = fixture.createGithubConnection();
        Instant originalUpdatedAt = original.updatedAt();

        IntegrationConnectionDetails firstUpdate = fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                original.id(),
                "First GitHub",
                null,
                Map.of(),
                originalUpdatedAt));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        original.id(),
                        "Second GitHub",
                        null,
                        Map.of(),
                        originalUpdatedAt)));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection was modified", exception.getMessage());
        assertEquals(originalUpdatedAt.plusNanos(1_000), firstUpdate.updatedAt());
        assertEquals("First GitHub", fixture.repository
                .findConnection(TENANT_ID, ORG_ID, original.id())
                .orElseThrow()
                .displayName());
    }

    @Test
    void serviceRejectsStaleDisableToken() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails original = fixture.createGithubConnection();
        Instant originalUpdatedAt = original.updatedAt();

        IntegrationConnectionDetails firstUpdate = fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                original.id(),
                "First GitHub",
                null,
                Map.of(),
                originalUpdatedAt));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.disableConnection(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        original.id(),
                        originalUpdatedAt));

        assertEquals("conflict", exception.code());
        assertEquals("Integration connection was modified", exception.getMessage());
        assertEquals(originalUpdatedAt.plusNanos(1_000), firstUpdate.updatedAt());
        assertEquals("active", fixture.repository
                .findConnection(TENANT_ID, ORG_ID, original.id())
                .orElseThrow()
                .status());
    }

    @Test
    void disablesConnectionWithNewImmutableDetailsObject() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        IntegrationConnectionDetails disabled = fixture.service.disableConnection(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                connection.updatedAt());

        assertNotSame(connection, disabled);
        assertEquals("active", connection.status());
        assertEquals("disabled", disabled.status());
        assertEquals(NOW.plusNanos(1_000), disabled.updatedAt());
    }

    @Test
    void rejectsMissingRequesterTenantOrgProviderAndDisplayName() {
        TestFixture fixture = new TestFixture();

        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                null,
                TENANT_ID,
                ORG_ID,
                "github",
                "Engineering GitHub",
                "123456",
                "Vericov",
                Map.of())));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                null,
                ORG_ID,
                "github",
                "Engineering GitHub",
                "123456",
                "Vericov",
                Map.of())));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                null,
                "github",
                "Engineering GitHub",
                "123456",
                "Vericov",
                Map.of())));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                " ",
                "Engineering GitHub",
                "123456",
                "Vericov",
                Map.of())));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "github",
                " ",
                "123456",
                "Vericov",
                Map.of())));
    }

    @Test
    void rejectsInvalidStatusAndConfigKey() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        assertValidationError(() -> fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                null,
                "paused",
                Map.of(),
                connection.updatedAt())));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "gitlab",
                "GitLab",
                "gitlab-1",
                "Vericov",
                Map.of("Bad Key", "value"))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "bitbucket",
                "Bitbucket",
                "bitbucket-1",
                "Vericov",
                Map.of("outer", Map.of("Bad Key", "value")))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "slack",
                "Slack",
                "slack-1",
                "Vericov",
                Map.of("outer", List.of(Map.of("Bad Key", "value"))))));
    }

    @Test
    void rejectsSecretBearingConnectionConfigKeysRecursively() {
        TestFixture fixture = new TestFixture();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        "gitlab",
                        "GitLab",
                        "gitlab-secret",
                        "Vericov",
                        Map.of("metadata", List.of(Map.of("access_token", "provider-secret"))))));

        assertEquals("validation_error", exception.code());
        assertTrue(exception.getMessage().contains("secret-bearing"));
    }

    @Test
    void rejectsSecretBearingBindingConfigKeysBeforePersistence() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        connection.id(),
                        "repository",
                        REPOSITORY_ID,
                        List.of("git.checks"),
                        Map.of("provider", Map.of("api-key", "do-not-store")),
                        "active")));

        assertEquals("validation_error", exception.code());
        assertTrue(exception.getMessage().contains("secret-bearing"));
        assertTrue(fixture.service.listBindings(REQUESTER_ID, TENANT_ID, ORG_ID, connection.id()).isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsNonJsonSafeConfigValuesWithValidationError() {
        TestFixture fixture = new TestFixture();
        Map nonStringKey = Map.of(1, "value");
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("valid_key", null);

        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "gitlab",
                "GitLab",
                "gitlab-1",
                "Vericov",
                (Map<String, Object>) nonStringKey)));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "bitbucket",
                "Bitbucket",
                "bitbucket-1",
                "Vericov",
                nullValue)));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "slack",
                "Slack",
                "slack-1",
                "Vericov",
                Map.of("custom", new Object()))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "openai",
                "OpenAI",
                "openai-1",
                "Vericov",
                Map.of("temperature", Double.NaN))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "jira",
                "Jira",
                "jira-1",
                "Vericov",
                Map.of("thresholds", List.of(Double.POSITIVE_INFINITY)))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "linear",
                "Linear",
                "linear-1",
                "Vericov",
                Map.of("nested", Map.of("score", Float.NaN)))));
        assertValidationError(() -> fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "github",
                "Mutable Number GitHub",
                "github-atomic",
                "Vericov",
                Map.of("count", new AtomicInteger(1)))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createConnectionDeepCopiesNestedConfigValues() {
        TestFixture fixture = new TestFixture();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("inner", "initial");
        List<Object> nestedList = new ArrayList<>();
        nestedList.add(Map.of("list_key", "initial"));
        Map<String, Object> config = new HashMap<>();
        config.put("outer", nestedMap);
        config.put("items", nestedList);

        IntegrationConnectionDetails connection = fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "gitlab",
                "GitLab",
                "gitlab-copy",
                "Vericov",
                config));
        nestedMap.put("inner", "changed");
        nestedMap.put("new_key", "late");
        nestedList.add(Map.of("late", "value"));

        assertEquals(Map.of("inner", "initial"), (Map<String, Object>) connection.config().get("outer"));
        assertEquals(List.of(Map.of("list_key", "initial")), connection.config().get("items"));
    }

    @Test
    void createCredentialStoresMetadataOnlyAndDoesNotExposeRawSecret() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        char[] rawSecret = "github-access-token".toCharArray();
        CreateCredentialCommand command = new CreateCredentialCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                " OAuth_Access_Token ",
                rawSecret,
                NOW.plusSeconds(3600));
        rawSecret[0] = 'X';

        IntegrationCredentialDetails credential = fixture.service.createCredential(command);

        assertEquals(TENANT_ID, credential.tenantId());
        assertEquals(connection.id(), credential.connectionId());
        assertEquals("oauth_access_token", credential.credentialKind());
        assertEquals("active", credential.status());
        assertEquals(1, credential.keyVersion());
        assertEquals(NOW.plusSeconds(3600), credential.expiresAt());
        assertEquals(NOW, credential.lastRotatedAt());
        assertEquals(NOW, credential.createdAt());
        assertEquals(NOW, credential.updatedAt());
        assertTrue(credential.secretRef().startsWith("vault://memory/"));
        assertFalse(credential.secretRef().contains("github-access-token"));
        assertFalse(credential.toString().contains("github-access-token"));
        assertEquals(List.of(credential), fixture.repository.listCredentials(TENANT_ID, ORG_ID, connection.id()));
    }

    @Test
    void createCredentialRejectsDuplicateActiveKindForConnection() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.createCredential(connection.id(), "api_token", "first-token");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.createCredential(connection.id(), "api_token", "second-token"));

        assertEquals("conflict", exception.code());
        assertEquals("Integration credential already exists", exception.getMessage());
    }

    @Test
    void leasesActiveCredentialWithDefensiveSecretCopies() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        char[] rawSecret = "github-refresh-token".toCharArray();
        CreateCredentialCommand command = new CreateCredentialCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "oauth_refresh_token",
                rawSecret,
                NOW.plusSeconds(7200));
        rawSecret[0] = 'X';
        IntegrationCredentialDetails credential = fixture.service.createCredential(command);

        CredentialLease lease = fixture.service.leaseCredential(
                "git-integration-service",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                " OAuth_Refresh_Token ");

        assertEquals(credential.secretRef(), lease.secretRef());
        assertTrue(lease.expiresAt().isAfter(NOW));
        assertTrue(lease.expiresAt().isBefore(NOW.plusSeconds(601)));
        assertEquals("github-refresh-token", new String(lease.secret()));

        char[] leasedSecret = lease.secret();
        leasedSecret[0] = 'X';
        CredentialLease secondLease = fixture.service.leaseCredential(
                "git-integration-service",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "oauth_refresh_token");

        assertEquals("github-refresh-token", new String(secondLease.secret()));
    }

    @Test
    void disabledOrRevokedConnectionsCannotLeaseCredentials() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails disabledConnection = fixture.createGithubConnection();
        fixture.createCredential(disabledConnection.id(), "webhook_secret", "disabled-secret");
        fixture.service.disableConnection(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                disabledConnection.id(),
                disabledConnection.updatedAt());

        assertNotFound(() -> fixture.service.leaseCredential(
                "webhook-worker",
                TENANT_ID,
                ORG_ID,
                disabledConnection.id(),
                "webhook_secret"));

        IntegrationConnectionDetails revokedConnection = fixture.service.createConnection(new CreateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                "github",
                "Revoked GitHub",
                "revoked-account",
                "Vericov",
                Map.of()));
        fixture.createCredential(revokedConnection.id(), "webhook_secret", "revoked-secret");
        fixture.service.updateConnection(new UpdateIntegrationConnectionCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                revokedConnection.id(),
                null,
                "revoked",
                Map.of(),
                revokedConnection.updatedAt()));

        assertNotFound(() -> fixture.service.leaseCredential(
                "webhook-worker",
                TENANT_ID,
                ORG_ID,
                revokedConnection.id(),
                "webhook_secret"));
    }

    @Test
    void integrationConnectionDetailsRequiresExternalAccountId() {
        assertThrows(
                NullPointerException.class,
                () -> connectionDetails(null, " Vericov "));
        assertThrows(
                IllegalArgumentException.class,
                () -> connectionDetails(" ", " Vericov "));

        IntegrationConnectionDetails details = connectionDetails(" 123456 ", " Vericov ");

        assertEquals("123456", details.externalAccountId());
        assertEquals("Vericov", details.externalAccountName());
    }

    @Test
    void domainRecordConstructionRejectsUnsupportedConfigValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> connectionDetails("123456", "Vericov", Map.of("count", new AtomicInteger(1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderDefinition(
                        "github",
                        "git",
                        "GitHub",
                        "github_app",
                        List.of(),
                        Map.of("count", new AtomicInteger(1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderDefinition(
                        "github",
                        "git",
                        "GitHub",
                        "github_app",
                        List.of(),
                        Map.of("client_secret", "do-not-store")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegrationBindingDetails(
                        UUID.fromString("00000000-0000-0000-0000-000000000888"),
                        TENANT_ID,
                        UUID.fromString("00000000-0000-0000-0000-000000000777"),
                        "repository",
                        ORG_ID,
                        List.of("git.checks"),
                        Map.of("count", new AtomicInteger(1)),
                        "active",
                        NOW,
                        NOW));

        ProviderDefinition provider = new ProviderDefinition(
                "github",
                "git",
                "GitHub",
                "github_app",
                List.of(),
                Map.of("limits", Map.of("max", 10)));
        IntegrationBindingDetails binding = new IntegrationBindingDetails(
                UUID.fromString("00000000-0000-0000-0000-000000000888"),
                TENANT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000777"),
                "repository",
                ORG_ID,
                List.of("git.checks"),
                Map.of("limits", Map.of("max", 10)),
                "active",
                NOW,
                NOW);

        assertEquals(Map.of("max", 10), provider.defaultConfig().get("limits"));
        assertEquals(Map.of("max", 10), binding.config().get("limits"));
    }

    @Test
    void listsConnectionsDeterministicallyWhenDisplayNameAndCreatedAtTie() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails second = fixture.saveConnection(
                "00000000-0000-0000-0000-000000000222",
                "Shared Name",
                "disabled");
        IntegrationConnectionDetails first = fixture.saveConnection(
                "00000000-0000-0000-0000-000000000111",
                "Shared Name",
                "revoked");

        List<IntegrationConnectionDetails> connections = fixture.service.listConnections(REQUESTER_ID, TENANT_ID, ORG_ID);

        assertEquals(List.of(first.id(), second.id()), connections.stream()
                .map(IntegrationConnectionDetails::id)
                .toList());
    }

    @Test
    void listConnectionsExcludesSameOrgRowsFromOtherTenants() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails visible = fixture.createGithubConnection();
        fixture.saveConnection(
                OTHER_TENANT_ID,
                "00000000-0000-0000-0000-000000000333",
                "Other Tenant GitHub",
                "disabled");

        List<IntegrationConnectionDetails> connections = fixture.service.listConnections(REQUESTER_ID, TENANT_ID, ORG_ID);

        assertEquals(List.of(visible.id()), connections.stream()
                .map(IntegrationConnectionDetails::id)
                .toList());
    }

    @Test
    void repositoryBindingCanGrantChecksAndComments() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        IntegrationBindingDetails binding = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                REPOSITORY_ID,
                List.of("git.checks", "git.comments"),
                Map.of("required_context", "coverage"),
                "active"));

        assertEquals(TENANT_ID, binding.tenantId());
        assertEquals(connection.id(), binding.connectionId());
        assertEquals("repository", binding.scopeType());
        assertEquals(REPOSITORY_ID, binding.scopeId());
        assertEquals(List.of("git.checks", "git.comments"), binding.capabilities());
        assertEquals(Map.of("required_context", "coverage"), binding.config());
        assertEquals("active", binding.status());
        assertEquals(List.of(binding.id()), fixture.service
                .listBindings(REQUESTER_ID, TENANT_ID, ORG_ID, connection.id())
                .stream()
                .map(IntegrationBindingDetails::id)
                .toList());
    }

    @Test
    void bindingUnsupportedProviderCapabilityFailsValidation() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        connection.id(),
                        "repository",
                        UUID.fromString("66666666-6666-6666-6666-666666666666"),
                        List.of("chat.notifications"),
                        Map.of(),
                        "active")));

        assertEquals("validation_error", exception.code());
    }

    @Test
    void resolvesActiveConnectionAndBindingForRepositoryCapability() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.createCredential(connection.id(), "github_app_private_key", "github-app-key");
        IntegrationBindingDetails binding = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                " repository ",
                REPOSITORY_ID,
                List.of("git.checks", "git.comments"),
                Map.of(),
                " active "));

        ResolvedIntegration resolved = fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                " GitHub ",
                " repository ",
                REPOSITORY_ID,
                " git.checks ");

        assertEquals(connection.id(), resolved.connection().id());
        assertEquals(binding.id(), resolved.binding().id());
        assertEquals("github_app_private_key", resolved.credentialKind());
    }

    @Test
    void resolveRequiresActiveCredentialForProviderCapability() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                REPOSITORY_ID,
                List.of("git.checks"),
                Map.of(),
                "active"));

        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                REPOSITORY_ID,
                "git.checks"));
    }

    @Test
    void disabledBindingsAreIgnoredByResolution() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        IntegrationBindingDetails binding = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        fixture.service.disableBinding(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                binding.updatedAt());

        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                repositoryId,
                "git.checks"));
    }

    @Test
    void repositoryResolutionDoesNotFallBackToOrganizationBinding() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "organization",
                ORG_ID,
                List.of("git.checks"),
                Map.of(),
                "active"));

        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                REPOSITORY_ID,
                "git.checks"));
        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                OTHER_REPOSITORY_ID,
                "git.checks"));
    }

    @Test
    void rejectsRepositoryScopeThatDoesNotBelongToTenantOrgBeforeBindingIsStored() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.scopeValidator.allowRepository(TENANT_ID, OTHER_ORG_ID, OTHER_REPOSITORY_ID);

        assertNotFound(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                OTHER_REPOSITORY_ID,
                List.of("git.checks"),
                Map.of(),
                "active")));
        assertNotFound(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                List.of("git.checks"),
                Map.of(),
                "active")));

        assertTrue(fixture.service.listBindings(REQUESTER_ID, TENANT_ID, ORG_ID, connection.id()).isEmpty());
    }

    @Test
    void acceptsComponentScopeWhenCanonicalComponentOwnershipExists() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        fixture.scopeValidator.allowComponent(TENANT_ID, ORG_ID, COMPONENT_ID);

        IntegrationBindingDetails binding = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "component",
                COMPONENT_ID,
                List.of("git.checks"),
                Map.of(),
                "active"));

        assertEquals(COMPONENT_ID, binding.scopeId());
        assertNotFound(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "component",
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                List.of("git.checks"),
                Map.of(),
                "active")));
    }

    @Test
    void disabledConnectionIsIgnoredByResolution() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        fixture.service.disableConnection(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                connection.updatedAt());

        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                repositoryId,
                "git.checks"));
    }

    @Test
    void resolveIsTenantAndOrgScoped() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));

        assertNotFound(() -> fixture.service.resolveIntegration(
                OTHER_TENANT_ID,
                ORG_ID,
                "github",
                "repository",
                repositoryId,
                "git.checks"));
        assertNotFound(() -> fixture.service.resolveIntegration(
                TENANT_ID,
                OTHER_ORG_ID,
                "github",
                "repository",
                repositoryId,
                "git.checks"));
    }

    @Test
    void bindingOperationsValidateScopeStatusCapabilitiesAndConnectionScope() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        assertValidationError(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "path",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active")));
        assertValidationError(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "paused")));
        assertValidationError(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of(),
                Map.of(),
                "active")));
        assertValidationError(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of("Bad Key", "value"),
                "active")));
        assertNotFound(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                OTHER_TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active")));
        assertNotFound(() -> fixture.service.disableBinding(
                REQUESTER_ID,
                TENANT_ID,
                OTHER_ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                NOW));
        assertNotFound(() -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active")));
    }

    @Test
    void upsertBindingUpdatesExistingCompositeBinding() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        IntegrationBindingDetails first = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of("required_context", "coverage"),
                "active"));

        IntegrationBindingDetails second = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.comments"),
                Map.of("required_context", "review"),
                "disabled",
                first.updatedAt()));

        assertEquals(first.id(), second.id());
        assertEquals(List.of("git.comments"), second.capabilities());
        assertEquals(Map.of("required_context", "review"), second.config());
        assertEquals("disabled", second.status());
        assertEquals(1, fixture.service.listBindings(REQUESTER_ID, TENANT_ID, ORG_ID, connection.id()).size());
        assertTrue(second.updatedAt().isAfter(first.updatedAt()));
    }

    @Test
    void rejectsStaleBindingUpdateToken() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        IntegrationBindingDetails original = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        IntegrationBindingDetails firstUpdate = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.comments"),
                Map.of(),
                "active",
                original.updatedAt()));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        connection.id(),
                        "repository",
                        repositoryId,
                        List.of("git.checks"),
                        Map.of(),
                        "active",
                        original.updatedAt())));

        assertEquals("conflict", exception.code());
        assertEquals("Integration binding was modified", exception.getMessage());
        assertEquals(original.updatedAt().plusNanos(1), firstUpdate.updatedAt());
        assertEquals(List.of("git.comments"), fixture.repository
                .findBinding(TENANT_ID, ORG_ID, connection.id(), "repository", repositoryId)
                .orElseThrow()
                .capabilities());
    }

    @Test
    void rejectsStaleBindingDisableToken() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        IntegrationBindingDetails original = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        IntegrationBindingDetails firstUpdate = fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.comments"),
                Map.of(),
                "active",
                original.updatedAt()));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.disableBinding(
                        REQUESTER_ID,
                        TENANT_ID,
                        ORG_ID,
                        connection.id(),
                        "repository",
                        repositoryId,
                        original.updatedAt()));

        assertEquals("conflict", exception.code());
        assertEquals("Integration binding was modified", exception.getMessage());
        assertEquals(original.updatedAt().plusNanos(1), firstUpdate.updatedAt());
        assertEquals("active", fixture.repository
                .findBinding(TENANT_ID, ORG_ID, connection.id(), "repository", repositoryId)
                .orElseThrow()
                .status());
    }

    @Test
    void repositoryUpsertUsesStoredBindingUpdatedAtForMonotonicProgression() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Instant futureUpdatedAt = NOW.plusSeconds(30);
        IntegrationBindingDetails current = fixture.repository.upsertBinding(new IntegrationBindingDetails(
                UUID.fromString("00000000-0000-0000-0000-000000000888"),
                TENANT_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active",
                NOW,
                futureUpdatedAt), null);

        IntegrationBindingDetails updated = fixture.repository.upsertBinding(new IntegrationBindingDetails(
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                TENANT_ID,
                connection.id(),
                "repository",
                repositoryId,
                List.of("git.comments"),
                Map.of(),
                "active",
                NOW,
                NOW), current.updatedAt());

        assertEquals(current.id(), updated.id());
        assertEquals(futureUpdatedAt.plusNanos(1), updated.updatedAt());
        assertEquals(List.of("git.comments"), updated.capabilities());
    }

    @Test
    void rejectsAmbiguousIntegrationResolution() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails firstConnection = fixture.createGithubConnection();
        IntegrationConnectionDetails secondConnection = fixture.saveConnection(
                TENANT_ID,
                "00000000-0000-0000-0000-000000000444",
                "Second GitHub",
                "active",
                "789012");
        UUID repositoryId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                firstConnection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        fixture.service.upsertBinding(new UpsertIntegrationBindingCommand(
                REQUESTER_ID,
                TENANT_ID,
                ORG_ID,
                secondConnection.id(),
                "repository",
                repositoryId,
                List.of("git.checks"),
                Map.of(),
                "active"));
        fixture.createCredential(firstConnection.id(), "github_app_private_key", "first-key");
        fixture.createCredential(secondConnection.id(), "github_app_private_key", "second-key");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.resolveIntegration(
                        TENANT_ID,
                        ORG_ID,
                        "github",
                        "repository",
                        repositoryId,
                        "git.checks"));

        assertEquals("conflict", exception.code());
        assertEquals("Integration resolution is ambiguous", exception.getMessage());
    }

    @Test
    void rejectsSecretBearingInternalSyncStateJsonMaps() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        assertValidationError(() -> fixture.service.updateSyncState(new UpdateIntegrationSyncStateCommand(
                "git-integration",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository_full",
                "repository",
                REPOSITORY_ID,
                "running",
                Map.of("next", Map.of("refresh_token", "do-not-store")),
                Map.of(),
                Map.of(),
                NOW.minusSeconds(60),
                null,
                NOW.plusSeconds(300),
                NOW.plusSeconds(120))));
        assertValidationError(() -> fixture.service.updateSyncState(new UpdateIntegrationSyncStateCommand(
                "git-integration",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "repository_full",
                "repository",
                REPOSITORY_ID,
                "failed",
                Map.of(),
                Map.of(),
                Map.of("provider", List.of(Map.of("private_key", "do-not-store"))),
                NOW.minusSeconds(60),
                NOW,
                null,
                null)));
    }

    @Test
    void rejectsSecretBearingInternalEventPayloadAndErrorMaps() {
        TestFixture fixture = new TestFixture();
        IntegrationConnectionDetails connection = fixture.createGithubConnection();

        assertValidationError(() -> fixture.service.recordEvent(new RecordIntegrationEventCommand(
                "git-integration",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "github",
                "git.check.updated",
                "evt-1",
                "repository",
                REPOSITORY_ID,
                "processed",
                Map.of("provider", List.of(Map.of("client_secret", "do-not-store"))),
                Map.of(),
                NOW,
                NOW.plusSeconds(1))));
        assertValidationError(() -> fixture.service.recordEvent(new RecordIntegrationEventCommand(
                "git-integration",
                TENANT_ID,
                ORG_ID,
                connection.id(),
                "github",
                "git.check.failed",
                "evt-2",
                "repository",
                REPOSITORY_ID,
                "failed",
                Map.of(),
                Map.of("authorization", "do-not-store"),
                NOW,
                NOW.plusSeconds(1))));
    }

    private static void assertValidationError(ExecutableCall call) {
        IntegrationException exception = assertThrows(IntegrationException.class, call::execute);
        assertEquals("validation_error", exception.code());
    }

    private static void assertNotFound(ExecutableCall call) {
        IntegrationException exception = assertThrows(IntegrationException.class, call::execute);
        assertEquals("not_found", exception.code());
    }

    private static IntegrationConnectionDetails connectionDetails(
            String externalAccountId,
            String externalAccountName) {
        return connectionDetails(externalAccountId, externalAccountName, Map.of());
    }

    private static IntegrationConnectionDetails connectionDetails(
            String externalAccountId,
            String externalAccountName,
            Map<String, Object> config) {
        return new IntegrationConnectionDetails(
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                TENANT_ID,
                ORG_ID,
                "github",
                "git",
                "GitHub",
                externalAccountId,
                externalAccountName,
                "active",
                config,
                REQUESTER_ID,
                null,
                NOW,
                NOW);
    }

    @FunctionalInterface
    private interface ExecutableCall {
        void execute();
    }

    private static final class TestFixture {
        private final InMemoryIntegrationRepository repository;
        private final TestScopeValidator scopeValidator;
        private final IntegrationApplicationService service;

        private TestFixture() {
            this(Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private TestFixture(Clock clock) {
            repository = new InMemoryIntegrationRepository();
            scopeValidator = new TestScopeValidator()
                    .allowOrganization(TENANT_ID, ORG_ID)
                    .allowOrganization(OTHER_TENANT_ID, ORG_ID)
                    .allowRepository(TENANT_ID, ORG_ID, REPOSITORY_ID)
                    .allowRepository(OTHER_TENANT_ID, ORG_ID, REPOSITORY_ID);
            service = new IntegrationApplicationService(
                    repository,
                    StaticProviderRegistry.defaultRegistry(),
                    new TestCredentialVault(clock),
                    scopeValidator,
                    clock);
        }

        private IntegrationConnectionDetails createGithubConnection() {
            return service.createConnection(new CreateIntegrationConnectionCommand(
                    REQUESTER_ID,
                    TENANT_ID,
                    ORG_ID,
                    "github",
                    "Engineering GitHub",
                    "123456",
                    "Vericov",
                    Map.of("installation_id", "123456")));
        }

        private IntegrationConnectionDetails saveConnection(String id, String displayName, String status) {
            return saveConnection(TENANT_ID, id, displayName, status);
        }

        private IntegrationConnectionDetails saveConnection(UUID tenantId, String id, String displayName, String status) {
            return saveConnection(tenantId, id, displayName, status, "123456");
        }

        private IntegrationConnectionDetails saveConnection(
                UUID tenantId,
                String id,
                String displayName,
                String status,
                String externalAccountId) {
            return repository.saveConnection(new IntegrationConnectionDetails(
                    UUID.fromString(id),
                    tenantId,
                    ORG_ID,
                    "github",
                    "git",
                    displayName,
                    externalAccountId,
                    "Vericov",
                    status,
                    Map.of(),
                    REQUESTER_ID,
                    null,
                    NOW,
                    NOW));
        }

        private IntegrationCredentialDetails createCredential(UUID connectionId, String credentialKind, String secret) {
            return service.createCredential(new CreateCredentialCommand(
                    REQUESTER_ID,
                    TENANT_ID,
                    ORG_ID,
                    connectionId,
                    credentialKind,
                    secret.toCharArray(),
                    NOW.plusSeconds(3600)));
        }
    }

    private static final class TestScopeValidator implements IntegrationScopeValidator {
        private final Set<ScopeKey> organizations = new HashSet<>();
        private final Set<ScopeKey> repositories = new HashSet<>();
        private final Set<ScopeKey> components = new HashSet<>();

        private TestScopeValidator allowOrganization(UUID tenantId, UUID orgId) {
            organizations.add(new ScopeKey(tenantId, orgId, orgId));
            return this;
        }

        private TestScopeValidator allowRepository(UUID tenantId, UUID orgId, UUID repositoryId) {
            repositories.add(new ScopeKey(tenantId, orgId, repositoryId));
            return this;
        }

        private TestScopeValidator allowComponent(UUID tenantId, UUID orgId, UUID componentId) {
            components.add(new ScopeKey(tenantId, orgId, componentId));
            return this;
        }

        @Override
        public void requireScope(UUID tenantId, UUID orgId, String scopeType, UUID scopeId) {
            ScopeKey scopeKey = new ScopeKey(tenantId, orgId, scopeId);
            if ("organization".equals(scopeType)) {
                if (!orgId.equals(scopeId) || !organizations.contains(scopeKey)) {
                    throw new IntegrationException("not_found", "Integration scope not found");
                }
                return;
            }
            if ("repository".equals(scopeType)) {
                if (!repositories.contains(scopeKey)) {
                    throw new IntegrationException("not_found", "Integration scope not found");
                }
                return;
            }
            if ("component".equals(scopeType)) {
                if (!components.contains(scopeKey)) {
                    throw new IntegrationException("not_found", "Integration scope not found");
                }
                return;
            }
            throw new IntegrationException("validation_error", "scope_type is invalid");
        }
    }

    private record ScopeKey(UUID tenantId, UUID orgId, UUID scopeId) {
    }

    private static final class TestCredentialVault implements CredentialVault {
        private final Clock clock;
        private final Map<String, StoredSecret> secretsByRef = new HashMap<>();

        private TestCredentialVault(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized String store(UUID tenantId, UUID connectionId, String credentialKind, char[] secret) {
            String secretRef = "vault://memory/" + UUID.randomUUID();
            secretsByRef.put(secretRef, new StoredSecret(
                    tenantId,
                    connectionId,
                    credentialKind,
                    Arrays.copyOf(secret, secret.length)));
            return secretRef;
        }

        @Override
        public synchronized CredentialLease lease(UUID tenantId, UUID connectionId, String secretRef, String requestedBy) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored == null
                    || !stored.tenantId().equals(tenantId)
                    || !stored.connectionId().equals(connectionId)
                    || requestedBy == null
                    || requestedBy.isBlank()) {
                throw new IntegrationException("not_found", "Integration credential not found");
            }
            return new CredentialLease(
                    secretRef,
                    Arrays.copyOf(stored.secret(), stored.secret().length),
                    clock.instant().plusSeconds(300));
        }

        @Override
        public synchronized void revoke(UUID tenantId, String secretRef) {
            StoredSecret stored = secretsByRef.get(secretRef);
            if (stored != null && stored.tenantId().equals(tenantId)) {
                secretsByRef.remove(secretRef);
            }
        }
    }

    private record StoredSecret(
            UUID tenantId,
            UUID connectionId,
            String credentialKind,
            char[] secret) {

        private StoredSecret {
            secret = Arrays.copyOf(secret, secret.length);
        }

        @Override
        public char[] secret() {
            return Arrays.copyOf(secret, secret.length);
        }
    }

}
