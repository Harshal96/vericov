package dev.vericov.integrations.application;

import dev.vericov.integrations.config.StaticProviderRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-05-23T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-23T11:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CONNECTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void includesGitProvidersWithCommonCapabilities() {
        StaticProviderRegistry registry = StaticProviderRegistry.defaultRegistry();

        ProviderDefinition github = registry.requireProvider("github");

        assertEquals("git", github.type());
        assertEquals("GitHub", github.displayName());
        assertTrue(github.capabilities().contains("git.webhooks"));
        assertTrue(github.capabilities().contains("git.checks"));
        assertTrue(github.capabilities().contains("git.pull_requests"));
        assertEquals("github_app_private_key", github.credentialKindForCapability(" git.checks "));
        assertEquals("webhook_secret", github.credentialKindForCapability("git.webhooks"));
    }

    @Test
    void findsAndFiltersProvidersWithCanonicalKeysAndTypes() {
        StaticProviderRegistry registry = new StaticProviderRegistry(List.of(
                new ProviderDefinition(" GitHub ", " Git ", " GitHub ", " GitHub_App ", List.of(), Map.of())));

        ProviderDefinition github = registry.requireProvider(" GITHUB ");

        assertEquals("github", github.providerKey());
        assertEquals("git", github.type());
        assertEquals("GitHub", github.displayName());
        assertEquals("github_app", github.authStrategy());
        assertEquals(List.of(github), registry.listProviders("git"));
        assertEquals(List.of(github), registry.listProviders(" GIT "));
    }

    @Test
    void requireProviderFailsForUnknownProvider() {
        StaticProviderRegistry registry = StaticProviderRegistry.defaultRegistry();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> registry.requireProvider("missing"));

        assertEquals("not_found", exception.code());
    }

    @Test
    void rejectsDuplicateNormalizedProviderKeys() {
        List<ProviderDefinition> providers = List.of(
                new ProviderDefinition("github", "git", "GitHub", "github_app", List.of(), Map.of()),
                new ProviderDefinition(" GitHub ", "git", "GitHub Enterprise", "github_app", List.of(), Map.of()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StaticProviderRegistry(providers));

        assertTrue(exception.getMessage().contains("github"));
    }

    @Test
    void providerDefinitionDeepCopiesNestedDefaultConfig() {
        List<String> events = new ArrayList<>(List.of("pull_request"));
        Map<String, Object> nested = new HashMap<>();
        nested.put("events", events);
        Map<String, Object> config = new HashMap<>();
        config.put("webhook", nested);

        ProviderDefinition provider = new ProviderDefinition(
                "github",
                "git",
                "GitHub",
                "github_app",
                List.of(),
                config);

        events.add("issue_comment");
        nested.put("enabled", true);
        config.put("extra", "mutated");

        Map<?, ?> webhook = (Map<?, ?>) provider.defaultConfig().get("webhook");
        assertEquals(List.of("pull_request"), webhook.get("events"));
        assertThrows(UnsupportedOperationException.class, () -> provider.defaultConfig().put("extra", "blocked"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) webhook.get("events")).clear());
    }

    @Test
    void connectionDetailsCanonicalizeFieldsAndDeepCopyConfig() {
        List<String> events = new ArrayList<>(List.of("pull_request"));
        Map<String, Object> config = new HashMap<>();
        config.put("events", events);

        IntegrationConnectionDetails connection = new IntegrationConnectionDetails(
                ID,
                TENANT_ID,
                ORG_ID,
                " GitHub ",
                " Git ",
                " Primary GitHub ",
                "account-1",
                "Account One",
                " Active ",
                config,
                USER_ID,
                null,
                CREATED_AT,
                UPDATED_AT);

        events.add("issue_comment");
        config.put("mutated", true);

        assertEquals("github", connection.providerKey());
        assertEquals("git", connection.integrationType());
        assertEquals("Primary GitHub", connection.displayName());
        assertEquals("active", connection.status());
        assertEquals(List.of("pull_request"), connection.config().get("events"));
        assertThrows(UnsupportedOperationException.class, () -> connection.config().put("extra", "blocked"));
    }

    @Test
    void bindingDetailsCanonicalizeFieldsDeepCopyConfigAndCopyStatusChanges() {
        List<String> nested = new ArrayList<>(List.of("value"));
        Map<String, Object> config = new HashMap<>();
        config.put("nested", nested);

        IntegrationBindingDetails binding = new IntegrationBindingDetails(
                ID,
                TENANT_ID,
                CONNECTION_ID,
                " Repository ",
                ORG_ID,
                List.of("git.checks"),
                config,
                " Active ",
                CREATED_AT,
                UPDATED_AT);

        nested.add("mutated");
        config.put("other", "mutated");
        IntegrationBindingDetails disabled = binding.withStatus(" Disabled ", Instant.parse("2026-05-23T12:00:00Z"));

        assertEquals("repository", binding.scopeType());
        assertEquals("active", binding.status());
        assertEquals(List.of("value"), binding.config().get("nested"));
        assertEquals("disabled", disabled.status());
        assertNotSame(binding, disabled);
        assertThrows(UnsupportedOperationException.class, () -> disabled.config().put("extra", "blocked"));
    }

    @Test
    void credentialDetailsCanonicalizeFieldsAndRejectInvalidValues() {
        IntegrationCredentialDetails credential = new IntegrationCredentialDetails(
                ID,
                TENANT_ID,
                CONNECTION_ID,
                " OAuth_Token ",
                " vault/ref ",
                1,
                " Active ",
                null,
                null,
                CREATED_AT,
                UPDATED_AT);

        assertEquals("oauth_token", credential.credentialKind());
        assertEquals("vault/ref", credential.secretRef());
        assertEquals("active", credential.status());
        assertEquals("disabled", credential.withStatus(" Disabled ", UPDATED_AT).status());
        assertThrows(IllegalArgumentException.class, () -> new IntegrationCredentialDetails(
                ID,
                TENANT_ID,
                CONNECTION_ID,
                "oauth_token",
                "vault/ref",
                0,
                "active",
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
        assertThrows(IllegalArgumentException.class, () -> new IntegrationCredentialDetails(
                ID,
                TENANT_ID,
                CONNECTION_ID,
                "oauth_token",
                " ",
                1,
                "active",
                null,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    @Test
    void rejectsNonStringNestedConfigKeysAndBlankCanonicalFields() {
        Map<Object, Object> nested = new HashMap<>();
        nested.put(1, "not allowed");
        Map<String, Object> config = new HashMap<>();
        config.put("nested", nested);

        assertThrows(IllegalArgumentException.class, () -> new ProviderDefinition(
                "github",
                "git",
                "GitHub",
                "github_app",
                List.of(),
                config));
        assertThrows(IllegalArgumentException.class, () -> new ProviderDefinition(
                " ",
                "git",
                "GitHub",
                "github_app",
                List.of(),
                Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProviderDefinition(
                "github",
                "git",
                "GitHub",
                "github_app",
                List.of("git.checks"),
                Map.of(),
                Map.of("git.unknown", "github_app_private_key")));
    }
}
