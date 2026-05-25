package dev.vericov.git.application.port;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ResolvedGitIntegration(
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        UUID repositoryId,
        String providerKey,
        String connectionStatus,
        String bindingStatus,
        String externalRepositoryId,
        String credentialKind,
        Set<String> capabilities,
        Map<String, Object> connectionConfig,
        Map<String, Object> bindingConfig) {

    public ResolvedGitIntegration {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(repositoryId, "repositoryId");
        providerKey = requireCanonical(providerKey, "providerKey");
        connectionStatus = requireCanonical(connectionStatus, "connectionStatus");
        bindingStatus = requireCanonical(bindingStatus, "bindingStatus");
        externalRepositoryId = requireTrimmed(externalRepositoryId, "externalRepositoryId");
        credentialKind = requireCanonical(credentialKind, "credentialKind");
        capabilities = copyCapabilities(capabilities);
        connectionConfig = copyMap(connectionConfig);
        bindingConfig = copyMap(bindingConfig);
    }

    public ResolvedGitIntegration(
            UUID tenantId,
            UUID orgId,
            UUID connectionId,
            UUID repositoryId,
            String providerKey,
            String connectionStatus,
            String bindingStatus,
            String externalRepositoryId,
            String credentialKind,
            Set<String> capabilities) {
        this(
                tenantId,
                orgId,
                connectionId,
                repositoryId,
                providerKey,
                connectionStatus,
                bindingStatus,
                externalRepositoryId,
                credentialKind,
                capabilities,
                Map.of(),
                Map.of());
    }

    public boolean grantsCapability(String capability) {
        return capabilities.contains(requireCanonical(capability, "capability"));
    }

    private static Set<String> copyCapabilities(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireCanonical(value, "capability"));
        }
        return Set.copyOf(normalized);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(values);
    }

    private static String requireCanonical(String value, String fieldName) {
        return requireTrimmed(value, fieldName).toLowerCase(Locale.ROOT);
    }

    private static String requireTrimmed(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
