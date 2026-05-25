package dev.vericov.integrations.api;

import dev.vericov.integrations.application.CreateCredentialCommand;
import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class CreateIntegrationCredentialHttpRequest {
    private final UUID tenantId;
    private final UUID orgId;
    private final String credentialKind;
    private final char[] secret;
    private final Instant expiresAt;

    public CreateIntegrationCredentialHttpRequest(
            @JsonbProperty("tenant_id") UUID tenantId,
            @JsonbProperty("org_id") UUID orgId,
            @JsonbProperty("credential_kind") String credentialKind,
            char[] secret,
            @JsonbProperty("expires_at") Instant expiresAt) {
        this.tenantId = tenantId;
        this.orgId = orgId;
        this.credentialKind = credentialKind;
        this.secret = secret == null ? null : Arrays.copyOf(secret, secret.length);
        this.expiresAt = expiresAt;
    }

    @JsonbProperty("tenant_id")
    public UUID tenantId() {
        return tenantId;
    }

    @JsonbProperty("org_id")
    public UUID orgId() {
        return orgId;
    }

    @JsonbProperty("credential_kind")
    public String credentialKind() {
        return credentialKind;
    }

    public char[] secret() {
        return secret == null ? null : Arrays.copyOf(secret, secret.length);
    }

    @JsonbProperty("expires_at")
    public Instant expiresAt() {
        return expiresAt;
    }

    public CreateCredentialCommand toCommand(UUID requesterUserId, UUID connectionId) {
        return new CreateCredentialCommand(
                requesterUserId,
                tenantId,
                orgId,
                connectionId,
                credentialKind,
                secret(),
                expiresAt);
    }
}
