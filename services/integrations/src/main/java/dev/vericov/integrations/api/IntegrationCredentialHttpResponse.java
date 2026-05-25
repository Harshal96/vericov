package dev.vericov.integrations.api;

import dev.vericov.integrations.application.IntegrationCredentialDetails;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.UUID;

public record IntegrationCredentialHttpResponse(
        UUID id,
        @JsonbProperty("tenant_id")
        UUID tenantId,
        @JsonbProperty("connection_id")
        UUID connectionId,
        @JsonbProperty("credential_kind")
        String credentialKind,
        @JsonbProperty("secret_ref")
        String secretRef,
        @JsonbProperty("key_version")
        int keyVersion,
        String status,
        @JsonbProperty("expires_at")
        Instant expiresAt,
        @JsonbProperty("last_rotated_at")
        Instant lastRotatedAt,
        @JsonbProperty("created_at")
        Instant createdAt,
        @JsonbProperty("updated_at")
        Instant updatedAt) {

    public static IntegrationCredentialHttpResponse from(IntegrationCredentialDetails credential) {
        return new IntegrationCredentialHttpResponse(
                credential.id(),
                credential.tenantId(),
                credential.connectionId(),
                credential.credentialKind(),
                credential.secretRef(),
                credential.keyVersion(),
                credential.status(),
                credential.expiresAt(),
                credential.lastRotatedAt(),
                credential.createdAt(),
                credential.updatedAt());
    }
}
