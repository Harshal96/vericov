package dev.vericov.integrations.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IntegrationCredentialDetails(
        UUID id,
        UUID tenantId,
        UUID connectionId,
        String credentialKind,
        String secretRef,
        int keyVersion,
        String status,
        Instant expiresAt,
        Instant lastRotatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public IntegrationCredentialDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(connectionId, "connectionId");
        credentialKind = IntegrationConfigValues.requireCanonical(credentialKind, "credentialKind");
        secretRef = IntegrationConfigValues.requireTrimmed(secretRef, "secretRef");
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        status = IntegrationConfigValues.requireCanonical(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public IntegrationCredentialDetails withStatus(String nextStatus, Instant updatedAt) {
        return new IntegrationCredentialDetails(
                id,
                tenantId,
                connectionId,
                credentialKind,
                secretRef,
                keyVersion,
                nextStatus,
                expiresAt,
                lastRotatedAt,
                createdAt,
                updatedAt);
    }
}
