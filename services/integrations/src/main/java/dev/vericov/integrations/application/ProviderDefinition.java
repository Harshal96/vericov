package dev.vericov.integrations.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ProviderDefinition(
        String providerKey,
        String type,
        String displayName,
        String authStrategy,
        List<String> capabilities,
        Map<String, Object> defaultConfig,
        Map<String, String> credentialKindByCapability) {

    private static final Set<String> CREDENTIAL_KINDS = Set.of(
            "oauth_access_token",
            "oauth_refresh_token",
            "github_app_private_key",
            "webhook_secret",
            "api_token");

    public ProviderDefinition(
            String providerKey,
            String type,
            String displayName,
            String authStrategy,
            List<String> capabilities,
            Map<String, Object> defaultConfig) {
        this(providerKey, type, displayName, authStrategy, capabilities, defaultConfig, Map.of());
    }

    public ProviderDefinition {
        providerKey = IntegrationConfigValues.requireCanonical(providerKey, "providerKey");
        type = IntegrationConfigValues.requireCanonical(type, "type");
        displayName = IntegrationConfigValues.requireTrimmed(displayName, "displayName");
        authStrategy = IntegrationConfigValues.requireCanonical(authStrategy, "authStrategy");
        capabilities = copyCapabilities(capabilities);
        defaultConfig = IntegrationConfigValues.deepCopyMap(defaultConfig);
        credentialKindByCapability = copyCredentialKinds(credentialKindByCapability, capabilities);
    }

    public String credentialKindForCapability(String capability) {
        String normalizedCapability = IntegrationConfigValues.requireCanonical(capability, "capability");
        String credentialKind = credentialKindByCapability.get(normalizedCapability);
        if (credentialKind == null) {
            throw new IntegrationException("validation_error", "capability credential kind is not configured");
        }
        return credentialKind;
    }

    private static List<String> copyCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> IntegrationConfigValues.requireCanonical(value, "capability"))
                .distinct()
                .toList();
    }

    private static Map<String, String> copyCredentialKinds(
            Map<String, String> values,
            List<String> providerCapabilities) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String capability = IntegrationConfigValues.requireCanonical(entry.getKey(), "capability");
            if (!providerCapabilities.contains(capability)) {
                throw new IllegalArgumentException("credential kind capability is not supported");
            }
            String credentialKind = IntegrationConfigValues.requireCanonical(entry.getValue(), "credentialKind");
            if (!CREDENTIAL_KINDS.contains(credentialKind)) {
                throw new IllegalArgumentException("credentialKind is invalid");
            }
            copy.put(capability, credentialKind);
        }
        return Map.copyOf(copy);
    }
}
