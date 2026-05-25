package dev.vericov.integrations.application;

import java.util.Objects;

public record ResolvedIntegration(
        IntegrationConnectionDetails connection,
        IntegrationBindingDetails binding,
        String credentialKind) {

    public ResolvedIntegration {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(binding, "binding");
        credentialKind = IntegrationConfigValues.requireCanonical(credentialKind, "credentialKind");
    }
}
